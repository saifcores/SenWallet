package sn.senwallet.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Notification payload. The consumer loads the operation from the ledger API;
 * the event does not copy the entries.
 */
public record LedgerTransactionPayload(UUID transactionId, String externalId, UUID ledgerId) {

    public LedgerTransactionPayload {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(externalId, "externalId");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        Objects.requireNonNull(ledgerId, "ledgerId");
    }
}
