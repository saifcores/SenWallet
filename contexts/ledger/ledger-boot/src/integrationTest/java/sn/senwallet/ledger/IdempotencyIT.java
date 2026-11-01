package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercise 4: the same request posted 1 000 times in a row, or 50 times at
 * once, creates exactly one transaction.
 */
class IdempotencyIT extends LedgerIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    private LedgerId ledger;
    private AccountId treasury;
    private AccountId wallet;

    @BeforeEach
    void setUp() {
        ledger = newLedger();
        treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        wallet = openAccount(ledger, "wallet", NormalBalance.CREDIT);
    }

    @Test
    void sequentialReplaysCreateOneTransaction() {
        var body = funding("topup:sequential", 25_000);
        var first = api().post("/ledger_transactions", body);
        assertThat(first.status()).isEqualTo(201);

        Api.Response last = null;
        for (int i = 0; i < 1_000; i++) {
            var replay = api().post("/ledger_transactions", body);
            assertThat(replay.status()).isEqualTo(200);
            if (last != null) {
                assertThat(replay.body()).isEqualTo(last.body());
            }
            last = replay;
        }

        assertThat(withoutBalances(last.body())).isEqualTo(withoutBalances(first.body()));
        assertStoredOnce("topup:sequential");
        assertThat(available()).isEqualTo(25_000);
    }

    @Test
    void concurrentFirstCallsCreateOneTransaction() throws Exception {
        var body = funding("topup:concurrent", 40_000);
        var start = new CountDownLatch(1);
        var calls = new ArrayList<Future<Api.Response>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    return api().post("/ledger_transactions", body);
                }));
            }
            start.countDown();
        }
        var statuses = new ArrayList<Integer>();
        var ids = new HashSet<String>();
        for (var call : calls) {
            var response = call.get();
            statuses.add(response.status());
            ids.add(response.body().get("id").asString());
        }

        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(ids).hasSize(1);
        assertStoredOnce("topup:concurrent");
        assertThat(available()).isEqualTo(40_000);
    }

    @Test
    void reusingTheExternalIdWithAnotherBodyIsAConflict() {
        api().post("/ledger_transactions", funding("topup:conflict", 10_000));

        var conflict = api().post("/ledger_transactions", funding("topup:conflict", 10_001));

        assertThat(conflict.status()).isEqualTo(409);
        assertStoredOnce("topup:conflict");
        assertThat(available()).isEqualTo(10_000);
    }

    private void assertStoredOnce(String externalId) {
        var headers = jdbc.sql("SELECT count(*) FROM ledger_transactions WHERE ledger_id = :ledger AND external_id = :id")
                .param("ledger", ledger.value()).param("id", externalId).query(Long.class).single();
        var entries = jdbc.sql("""
                SELECT count(*) FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.ledger_id = :ledger AND t.external_id = :id
                """)
                .param("ledger", ledger.value()).param("id", externalId).query(Long.class).single();
        assertThat(headers).isEqualTo(1);
        assertThat(entries).isEqualTo(2);
    }

    private long available() {
        return api().get("/ledger_accounts/" + wallet + "/balances").body().get("available").asLong();
    }

    private Map<String, Object> funding(String externalId, long amount) {
        return Map.of("ledger_id", ledger.toString(), "external_id", externalId, "entries", List.of(
                Map.of("account_id", treasury.toString(), "direction", "debit", "amount", amount),
                Map.of("account_id", wallet.toString(), "direction", "credit", "amount", amount)));
    }

    private static JsonNode withoutBalances(JsonNode body) {
        var copy = body.deepCopy();
        ((ObjectNode) copy).remove("balances");
        return copy;
    }
}
