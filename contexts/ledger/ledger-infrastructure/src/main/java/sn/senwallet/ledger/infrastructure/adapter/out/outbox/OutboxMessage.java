package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import java.util.List;
import java.util.UUID;

/** One unpublished outbox row, ready for the broker. */
public record OutboxMessage(long id, UUID eventId, String eventType, String body) {
}
