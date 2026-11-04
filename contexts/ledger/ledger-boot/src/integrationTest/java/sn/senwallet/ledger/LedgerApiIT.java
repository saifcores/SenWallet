package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HTTP round-trips: status codes, snake_case bodies and problem types. */
class LedgerApiIT extends LedgerIntegrationTest {

    private String ledger;
    private String treasury;
    private String adama;
    private String binta;

    @BeforeEach
    void setUp() {
        var created = api().post("/ledgers", Map.of("name", "senwallet", "environment", "test"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("created_at").asString()).isNotBlank();
        ledger = created.body().get("id").asString();
        treasury = account("treasury", "debit");
        adama = account("wallet adama", "credit");
        binta = account("wallet binta", "credit");
    }

    @Test
    void ledgerAndAccountsCanBeRead() {
        assertThat(api().get("/ledgers/" + ledger).body().get("environment").asString()).isEqualTo("test");

        var account = api().get("/ledger_accounts/" + adama);

        assertThat(account.status()).isEqualTo(200);
        assertThat(account.body().get("ledger_id").asString()).isEqualTo(ledger);
        assertThat(account.body().get("normal_balance").asString()).isEqualTo("credit");
        assertThat(account.body().get("balance_mode").asString()).isEqualTo("sync");
        assertThat(account.body().get("balances").get("available").asLong()).isZero();
    }

    @Test
    void postingReturns201ThenReplaysWith200() {
        var body = transfer("pispi-in:1", treasury, adama, 100_000, "posted", List.of());

        var first = api().post("/ledger_transactions", body);
        var replay = api().post("/ledger_transactions", body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().get("external_id").asString()).isEqualTo("pispi-in:1");
        assertThat(first.body().get("entries")).hasSize(2);
        assertThat(balanceIn(first, adama)).isEqualTo(100_000);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.body().get("id")).isEqualTo(first.body().get("id"));
        assertThat(available(adama)).isEqualTo(100_000);
    }

    @Test
    void sameExternalIdWithAnotherBodyIs409() {
        api().post("/ledger_transactions", transfer("pispi-in:1", treasury, adama, 100_000, "posted", List.of()));

        var conflict = api().post("/ledger_transactions",
                transfer("pispi-in:1", treasury, adama, 99_999, "posted", List.of()));

        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().get("type").asString()).isEqualTo("urn:senwallet:problem:idempotency_conflict");
        assertThat(conflict.body().get("external_id").asString()).isEqualTo("pispi-in:1");
    }

    @Test
    void violatedConditionIs422WithTheAccountAndCondition() {
        var overdraft = api().post("/ledger_transactions",
                transfer("p2p:1", adama, binta, 1, "posted", List.of(notOverdrawn(adama))));

        assertThat(overdraft.status()).isEqualTo(422);
        assertThat(overdraft.body().get("code").asString()).isEqualTo("balance_condition_violated");
        assertThat(overdraft.body().get("account_id").asString()).isEqualTo(adama);
        assertThat(overdraft.body().get("condition").get("operator").asString()).isEqualTo("gte");
        assertThat(overdraft.body().get("actual").asLong()).isEqualTo(-1);
        assertThat(api().get("/ledger_transactions/" + "00000000-0000-0000-0000-000000000000").status())
                .isEqualTo(404);
    }

    @Test
    void unbalancedAndInvalidBodiesAreRejected() {
        var unbalanced = Map.of("ledger_id", ledger, "external_id", "bad:1", "entries", List.of(
                Map.of("account_id", treasury, "direction", "debit", "amount", 10),
                Map.of("account_id", adama, "direction", "credit", "amount", 9)));
        var missingAmount = Map.of("ledger_id", ledger, "external_id", "bad:2", "entries", List.of(
                Map.of("account_id", treasury, "direction", "debit")));

        assertThat(api().post("/ledger_transactions", unbalanced).body().get("code").asString())
                .isEqualTo("unbalanced");
        var invalid = api().post("/ledger_transactions", missingAmount);
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().get("field").asString()).isEqualTo("entries[0].amount");
        assertThat(api().post("/ledger_transactions", "{not json").status()).isEqualTo(400);
        assertThat(api().get("/ledger_accounts/not-a-uuid").status()).isEqualTo(400);
    }

    @Test
    void pendingTransactionIsPostedOnceAndRetriesAreNoOps() {
        api().post("/ledger_transactions", transfer("pispi-in:1", treasury, adama, 100_000, "posted", List.of()));
        var hold = api().post("/ledger_transactions",
                transfer("cashout:1", adama, treasury, 30_000, "pending", List.of(notOverdrawn(adama))));
        var id = hold.body().get("id").asString();
        assertThat(available(adama)).isEqualTo(70_000);

        var posted = api().patch("/ledger_transactions/" + id, Map.of("status", "posted"));
        var retry = api().patch("/ledger_transactions/" + id, Map.of("status", "posted"));
        var archive = api().patch("/ledger_transactions/" + id, Map.of("status", "archived"));

        assertThat(posted.status()).isEqualTo(200);
        assertThat(posted.body().get("posted_at").asString()).isNotBlank();
        assertThat(retry.status()).isEqualTo(200);
        assertThat(archive.status()).isEqualTo(409);
        assertThat(archive.body().get("code").asString()).isEqualTo("illegal_transition");
        assertThat(api().get("/ledger_accounts/" + adama + "/balances").body().get("posted").asLong())
                .isEqualTo(70_000);
    }

    @Test
    void reversalUndoesAPostingOnce() {
        var funding = api().post("/ledger_transactions",
                transfer("pispi-in:1", treasury, adama, 100_000, "posted", List.of()));
        var id = funding.body().get("id").asString();

        var reversal = api().post("/ledger_transactions/" + id + "/reversal", "");
        var retry = api().post("/ledger_transactions/" + id + "/reversal", "");
        var other = api().post("/ledger_transactions/" + id + "/reversal", Map.of("external_id", "other"));

        assertThat(reversal.status()).isEqualTo(201);
        assertThat(reversal.body().get("reverses_id").asString()).isEqualTo(id);
        assertThat(reversal.body().get("external_id").asString()).isEqualTo("reversal:" + id);
        assertThat(retry.status()).isEqualTo(200);
        assertThat(other.status()).isEqualTo(409);
        assertThat(other.body().get("code").asString()).isEqualTo("transaction_already_reversed");
        assertThat(available(adama)).isZero();
    }

    @Test
    void balancesAsOfAPastInstant() throws InterruptedException {
        api().post("/ledger_transactions", transfer("pispi-in:1", treasury, adama, 100_000, "posted", List.of()));
        var hold = api().post("/ledger_transactions",
                transfer("cashout:1", adama, treasury, 30_000, "pending", List.of()));
        Thread.sleep(5);
        var beforeSettlement = Instant.now();
        Thread.sleep(5);
        api().patch("/ledger_transactions/" + hold.body().get("id").asString(), Map.of("status", "posted"));

        var then = api().get("/ledger_accounts/" + adama + "/balances?as_of=" + beforeSettlement);
        var now = api().get("/ledger_accounts/" + adama + "/balances");

        assertThat(then.body().get("as_of").asString()).isEqualTo(beforeSettlement.toString());
        assertThat(then.body().get("posted").asLong()).isEqualTo(100_000);
        assertThat(then.body().get("available").asLong()).isEqualTo(70_000);
        assertThat(now.body().get("posted").asLong()).isEqualTo(70_000);
        assertThat(now.body().has("as_of")).isFalse();
    }

    private String account(String name, String normal) {
        var created = api().post("/ledger_accounts", Map.of("ledger_id", ledger, "name", name,
                "normal_balance", normal, "metadata", Map.of("kind", name.split(" ")[0])));
        assertThat(created.status()).isEqualTo(201);
        return created.body().get("id").asString();
    }

    private long available(String account) {
        return api().get("/ledger_accounts/" + account + "/balances").body().get("available").asLong();
    }

    private static long balanceIn(Api.Response response, String account) {
        for (var balance : response.body().get("balances")) {
            if (balance.get("account_id").asString().equals(account)) {
                return balance.get("available").asLong();
            }
        }
        throw new AssertionError("no balances for " + account + " in " + response.raw());
    }

    static Map<String, Object> notOverdrawn(String account) {
        return Map.of("account_id", account, "balance", "available", "operator", "gte", "amount", 0);
    }

    Map<String, Object> transfer(
            String externalId, String from, String to, long amount, String status, List<Object> conditions) {
        return Map.of("ledger_id", ledger, "external_id", externalId, "status", status,
                "entries", List.of(
                        Map.of("account_id", from, "direction", "debit", "amount", amount),
                        Map.of("account_id", to, "direction", "credit", "amount", amount)),
                "conditions", conditions);
    }
}
