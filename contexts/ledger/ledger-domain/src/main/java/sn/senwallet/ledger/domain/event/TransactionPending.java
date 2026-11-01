package sn.senwallet.ledger.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.id.TransactionId;

public record TransactionPending(UUID eventId, Instant occurredAt, TransactionId transactionId, ExternalId externalId)
        implements LedgerTransactionEvent {

    public TransactionPending {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(externalId, "externalId");
    }

    public static TransactionPending of(TransactionId transactionId, ExternalId externalId, Instant occurredAt) {
        return new TransactionPending(UUID.randomUUID(), occurredAt, transactionId, externalId);
    }
}
