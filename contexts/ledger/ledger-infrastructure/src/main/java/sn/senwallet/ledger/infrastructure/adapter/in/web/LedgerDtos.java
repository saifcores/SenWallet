package sn.senwallet.ledger.infrastructure.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSON bodies of the ledger API (contracts/openapi/ledger.yaml). Property names
 * are snake_case, amounts are integers in minor units, enums are lower-case.
 */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record CreateLedgerRequest(String name, String environment) {
    }

    public record LedgerResponse(UUID id, String name, String environment, Instant createdAt) {
    }

    public record OpenAccountRequest(
            UUID ledgerId,
            String name,
            String normalBalance,
            String currency,
            String balanceMode,
            Map<String, String> metadata) {
    }

    public record AccountResponse(
            UUID id,
            UUID ledgerId,
            String name,
            String normalBalance,
            String currency,
            String balanceMode,
            String status,
            Map<String, String> metadata,
            BalancesResponse balances) {
    }

    public record BalancesResponse(
            UUID accountId,
            long posted,
            long pending,
            long available,
            long lockVersion,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant asOf) {
    }

    public record EntryRequest(UUID accountId, String direction, Long amount) {
    }

    public record ConditionRequest(UUID accountId, String balance, String operator, Long amount) {
    }

    public record PostTransactionRequest(
            UUID ledgerId,
            String externalId,
            String status,
            String currency,
            String description,
            Map<String, String> metadata,
            List<EntryRequest> entries,
            List<ConditionRequest> conditions) {
    }

    public record TransitionRequest(String status) {
    }

    public record ReversalRequest(String externalId, List<ConditionRequest> conditions) {
    }

    public record EntryResponse(UUID accountId, String direction, long amount) {
    }

    public record TransactionResponse(
            UUID id,
            UUID ledgerId,
            String externalId,
            String status,
            String currency,
            String description,
            Map<String, String> metadata,
            UUID reversesId,
            Instant effectiveAt,
            Instant postedAt,
            Instant archivedAt,
            List<EntryResponse> entries,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<BalancesResponse> balances) {
    }
}
