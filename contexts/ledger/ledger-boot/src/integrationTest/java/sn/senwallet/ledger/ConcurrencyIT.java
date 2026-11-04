package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import sn.senwallet.ledger.application.port.in.BalanceQuery;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.infrastructure.adapter.out.persistence.BalanceRecomputeQuery;
import sn.senwallet.shared.money.Money;

class ConcurrencyIT extends LedgerIntegrationTest {

    @Autowired
    PostTransactionUseCase post;

    @Autowired
    BalanceQuery balances;

    @Autowired
    BalanceRecomputeQuery recompute;

    @Autowired
    JdbcClient jdbc;

    /**
     * Exercise 5: 100 concurrent debits of 20 000 on an account holding
     * 1 000 000 XOF with {@code available >= 0}: exactly 50 succeed.
     */
    @Test
    void concurrentDebitsNeverOverdraw() throws Exception {
        var ledger = newLedger();
        var treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        var payer = openAccount(ledger, "payer", NormalBalance.CREDIT);
        var merchant = openAccount(ledger, "merchant", NormalBalance.CREDIT);
        post.post(CountersMatchEntriesIT.command(ledger, "fund", treasury, payer, 1_000_000,
                TransactionStatus.POSTED, List.of()));

        var start = new CountDownLatch(1);
        var calls = new ArrayList<Future<Api.Response>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                var body = Map.of("ledger_id", ledger.toString(), "external_id", "debit:" + i,
                        "entries", List.of(
                                Map.of("account_id", payer.toString(), "direction", "debit", "amount", 20_000),
                                Map.of("account_id", merchant.toString(), "direction", "credit", "amount", 20_000)),
                        "conditions", List.of(LedgerApiIT.notOverdrawn(payer.toString())));
                calls.add(executor.submit(() -> {
                    start.await();
                    return api().post("/ledger_transactions", body);
                }));
            }
            start.countDown();
        }
        var statuses = new ArrayList<Integer>();
        for (var call : calls) {
            statuses.add(call.get().status());
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(50);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(50);
        assertThat(balances.current(payer).available()).isEqualTo(Money.xof(0));
        assertThat(balances.current(merchant).posted()).isEqualTo(Money.xof(1_000_000));
        assertThat(jdbc.sql("""
                SELECT count(*) FROM account_balances
                WHERE least(posted_dr, posted_cr, pending_dr, pending_cr) < 0
                """).query(Long.class).single()).isZero();
        assertThat(recompute.compareAll()).filteredOn(BalanceRecomputeQuery.Comparison::drifted).isEmpty();
    }

    /**
     * Opposite P2P transfers lock the same two rows in the same order, so they
     * never deadlock.
     */
    @Test
    void crossedTransfersCompleteWithoutDeadlock() throws Exception {
        var ledger = newLedger();
        var treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        var adama = openAccount(ledger, "adama", NormalBalance.CREDIT);
        var binta = openAccount(ledger, "binta", NormalBalance.CREDIT);
        post.post(CountersMatchEntriesIT.command(ledger, "fund:a", treasury, adama, 1_000_000,
                TransactionStatus.POSTED, List.of()));
        post.post(CountersMatchEntriesIT.command(ledger, "fund:b", treasury, binta, 1_000_000,
                TransactionStatus.POSTED, List.of()));

        var start = new CountDownLatch(1);
        var calls = new ArrayList<Future<?>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                var forward = i % 2 == 0;
                var from = forward ? adama : binta;
                var to = forward ? binta : adama;
                var externalId = "p2p:" + i;
                calls.add(executor.submit(() -> {
                    start.await();
                    return post.post(CountersMatchEntriesIT.command(ledger, externalId, from, to, 1_000,
                            TransactionStatus.POSTED, List.of(BalanceCondition.of(from, BalanceType.AVAILABLE,
                                    Operator.GTE, Money.xof(0)))));
                }));
            }
            start.countDown();
        }
        for (var call : calls) {
            call.get();
        }

        assertThat(balances.current(adama).posted()).isEqualTo(Money.xof(1_000_000));
        assertThat(balances.current(binta).posted()).isEqualTo(Money.xof(1_000_000));
        assertThat(balances.current(adama).lockVersion()).isEqualTo(201);
    }
}
