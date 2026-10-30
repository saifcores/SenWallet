package sn.senwallet.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact that happened in a domain; {@code eventId} is the deduplication key
 * for consumers.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
