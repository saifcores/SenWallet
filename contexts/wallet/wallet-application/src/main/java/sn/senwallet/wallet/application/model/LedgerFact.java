package sn.senwallet.wallet.application.model;

import java.time.Instant;
import java.util.UUID;

/** A ledger fact the wallet applies. The payload is a notification (ADR-21). */
public record LedgerFact(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID transactionId,
        String externalId,
        UUID ledgerId) {
}
