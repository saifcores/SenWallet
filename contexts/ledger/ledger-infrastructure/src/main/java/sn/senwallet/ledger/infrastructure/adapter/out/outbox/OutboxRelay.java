package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads unpublished rows with {@code FOR UPDATE SKIP LOCKED}, publishes them,
 * then stamps {@code published_at}. A failed confirm rolls the stamp back;
 * the broker may already have the messages (at least once).
 */
@Component
public class OutboxRelay {

    private final JdbcClient jdbc;
    private final EventPublisher publisher;
    private final TransactionTemplate tx;
    private final int batchSize;

    public OutboxRelay(
            JdbcClient jdbc,
            EventPublisher publisher,
            PlatformTransactionManager transactions,
            @Value("${ledger.outbox.batch-size:100}") int batchSize) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.tx = new TransactionTemplate(transactions);
        this.batchSize = batchSize;
    }

    /** @return how many rows were published and stamped */
    public int publishBatch() {
        Integer count = tx.execute(status -> {
            var rows = lockUnpublished();
            if (rows.isEmpty()) {
                return 0;
            }
            publisher.publish(rows);
            markPublished(rows);
            return rows.size();
        });
        return count == null ? 0 : count;
    }

    private List<OutboxMessage> lockUnpublished() {
        return jdbc.sql("""
                SELECT id, event_id, event_type, payload::text AS payload
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
                """)
                .param("limit", batchSize)
                .query((rs, row) -> new OutboxMessage(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload")))
                .list();
    }

    private void markPublished(List<OutboxMessage> rows) {
        jdbc.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)")
                .param("ids", rows.stream().map(OutboxMessage::id).toList())
                .update();
    }
}
