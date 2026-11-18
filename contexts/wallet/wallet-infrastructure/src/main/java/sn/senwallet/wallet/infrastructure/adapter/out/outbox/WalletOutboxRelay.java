package sn.senwallet.wallet.infrastructure.adapter.out.outbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Publishes unpublished wallet facts to the {@code wallet} topic exchange. */
public class WalletOutboxRelay {

    static final String EXCHANGE = "wallet";

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final AmqpAdmin admin;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);

    public WalletOutboxRelay(
            JdbcClient jdbc,
            RabbitTemplate rabbit,
            AmqpAdmin admin,
            PlatformTransactionManager transactions,
            int batchSize) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.admin = admin;
        this.tx = new TransactionTemplate(transactions);
        this.batchSize = batchSize;
    }

    public int publishBatch() {
        Integer count = tx.execute(status -> {
            var rows = jdbc.sql("""
                    SELECT id, event_id, event_type, payload::text AS payload
                    FROM outbox
                    WHERE published_at IS NULL
                    ORDER BY id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                    """)
                    .param("limit", batchSize)
                    .query((rs, row) -> new Row(
                            rs.getLong("id"),
                            rs.getObject("event_id", UUID.class),
                            rs.getString("event_type"),
                            rs.getString("payload")))
                    .list();
            if (rows.isEmpty()) {
                return 0;
            }
            admin.declareExchange(exchange);
            rabbit.invoke(operations -> {
                for (var row : rows) {
                    operations.send(EXCHANGE, row.eventType(), MessageBuilder
                            .withBody(row.body().getBytes(StandardCharsets.UTF_8))
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setMessageId(row.eventId().toString())
                            .setType(row.eventType())
                            .build());
                }
                operations.waitForConfirmsOrDie(5_000);
                return null;
            });
            jdbc.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)")
                    .param("ids", rows.stream().map(Row::id).toList())
                    .update();
            return rows.size();
        });
        return count == null ? 0 : count;
    }

    private record Row(long id, UUID eventId, String eventType, String body) {
    }
}
