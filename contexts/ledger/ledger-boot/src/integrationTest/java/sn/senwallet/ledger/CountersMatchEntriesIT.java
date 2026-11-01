package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.application.command.TransitionCommand;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.application.port.in.ReverseTransactionUseCase;
import sn.senwallet.ledger.application.port.in.TransitionTransactionUseCase;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.infrastructure.adapter.out.persistence.BalanceRecomputeQuery;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Money;

/**
 * Exercise 3: after ~2 000 random postings, transitions and reversals, the
 * counters of every account equal the balances recomputed from the entries.
 */
class CountersMatchEntriesIT extends LedgerIntegrationTest {

    private static final int OPERATIONS = 2_000;

    @Autowired
    PostTransactionUseCase post;

    @Autowired
    TransitionTransactionUseCase transition;

    @Autowired
    ReverseTransactionUseCase reverse;

    @Autowired
    BalanceRecomputeQuery recompute;

    @Test
    void countersEqualTheRecomputedBalances() {
        var random = new Random(20261102L);
        var ledger = newLedger();
        var treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        var wallets = new ArrayList<AccountId>();
        for (int i = 0; i < 8; i++) {
            wallets.add(openAccount(ledger, "wallet " + i, NormalBalance.CREDIT));
        }
        var pending = new ArrayList<TransactionId>();
        var posted = new ArrayList<TransactionId>();
        int rejected = 0;

        for (int i = 0; i < OPERATIONS; i++) {
            var choice = random.nextInt(10);
            try {
                if (choice < 3 || posted.isEmpty()) {
                    var wallet = pick(random, wallets);
                    posted.add(post.post(command(ledger, "fund:" + i, treasury, wallet,
                            1 + random.nextInt(50_000), TransactionStatus.POSTED, List.of())).transaction().id());
                } else if (choice < 7) {
                    var from = pick(random, wallets);
                    var to = pick(random, wallets.stream().filter(w -> !w.equals(from)).toList());
                    var status = random.nextBoolean() ? TransactionStatus.POSTED : TransactionStatus.PENDING;
                    var txn = post.post(command(ledger, "p2p:" + i, from, to, 1 + random.nextInt(30_000), status,
                            List.of(BalanceCondition.of(from, BalanceType.AVAILABLE, Operator.GTE, Money.xof(0)))))
                            .transaction();
                    (status == TransactionStatus.POSTED ? posted : pending).add(txn.id());
                } else if (choice < 9 && !pending.isEmpty()) {
                    var id = pending.remove(random.nextInt(pending.size()));
                    var target = random.nextInt(3) == 0 ? TransactionStatus.ARCHIVED : TransactionStatus.POSTED;
                    transition.transition(new TransitionCommand(id, target));
                    if (target == TransactionStatus.POSTED) {
                        posted.add(id);
                    }
                } else {
                    var id = pick(random, posted);
                    posted.add(reverse.reverse(new ReverseTransactionCommand(id, Optional.empty(), List.of()))
                            .transaction().id());
                }
            } catch (BalanceConditionViolatedException | TransactionAlreadyReversedException e) {
                rejected++;
            }
        }

        var comparisons = recompute.compareAll();
        assertThat(comparisons).filteredOn(BalanceRecomputeQuery.Comparison::drifted).isEmpty();
        assertThat(comparisons.stream().map(BalanceRecomputeQuery.Comparison::accountId))
                .containsAll(wallets).contains(treasury);
        assertThat(rejected).as("some operations hit a condition").isPositive().isLessThan(OPERATIONS / 2);
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    static PostTransactionCommand command(LedgerId ledger, String externalId, AccountId from, AccountId to,
            long amount, TransactionStatus status, List<BalanceCondition> conditions) {
        return new PostTransactionCommand(ledger, ExternalId.of(externalId), status,
                List.of(Entry.debit(from, Amount.xof(amount)), Entry.credit(to, Amount.xof(amount))),
                conditions, null, Map.of());
    }
}
