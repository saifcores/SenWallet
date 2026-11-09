package sn.senwallet.clients.ledger;

import java.util.List;
import java.util.UUID;

/** Bodies and results of the ledger operations the wallet uses. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record CreateLedger(String name, String environment) {
    }

    public record OpenAccount(
            UUID ledgerId,
            String name,
            String normalBalance,
            String currency,
            String balanceMode) {
    }

    public record Entry(UUID accountId, String direction, long amount) {
    }

    public record Condition(UUID accountId, String balance, String operator, long amount) {
    }

    public record PostTransaction(
            UUID ledgerId,
            String externalId,
            String status,
            String currency,
            String description,
            List<Entry> entries,
            List<Condition> conditions) {
    }

    public record Line(UUID accountId, String direction, long amount) {
    }

    public record Transaction(UUID id, String externalId, List<Line> entries) {
    }

    public record Balances(long posted, long pending, long available) {
    }
}
