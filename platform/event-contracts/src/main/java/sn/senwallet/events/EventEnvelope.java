package sn.senwallet.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Common envelope for every published fact. {@code traceId} stays null until
 * tracing arrives (M10). {@code version} is the schema version.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String traceId,
        UUID aggregateId,
        int version,
        T payload) {

    public static final int VERSION = 1;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }
    }
}
