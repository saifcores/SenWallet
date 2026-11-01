package sn.senwallet.ledger.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.id.TransactionId;

public record TransactionPosted(UUID eventId, Instant occurredAt, TransactionId transactionId, ExternalId externalId)
        implements LedgerTransactionEvent {

    public TransactionPosted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(externalId, "externalId");
    }

    public static TransactionPosted of(TransactionId transactionId, ExternalId externalId, Instant occurredAt) {
        return new TransactionPosted(UUID.randomUUID(), occurredAt, transactionId, externalId);
    }
}
