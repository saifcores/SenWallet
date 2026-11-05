package sn.senwallet.events.inbox;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inserts {@code event_id} into {@code processed_events} and runs the handler
 * only when that insert wins. Caller supplies the transaction: a handler
 * failure rolls the claim back, and a second delivery is a no-op.
 */
public final class IdempotentInbox {

    private final JdbcClient jdbc;

    public IdempotentInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} when this delivery was the first and the handler ran */
    public boolean claim(UUID eventId, Runnable handler) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(handler, "handler");
        var inserted = jdbc.sql("""
                INSERT INTO processed_events (event_id, processed_at)
                VALUES (:eventId, now())
                ON CONFLICT DO NOTHING
                RETURNING event_id
                """)
                .param("eventId", eventId)
                .query((rs, row) -> rs.getObject("event_id", UUID.class))
                .optional();
        if (inserted.isEmpty()) {
            return false;
        }
        handler.run();
        return true;
    }
}
