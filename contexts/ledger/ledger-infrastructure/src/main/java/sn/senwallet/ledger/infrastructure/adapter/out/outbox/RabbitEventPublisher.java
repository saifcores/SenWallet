package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Topic exchange {@code ledger}. The body is the envelope JSON; the routing key
 * is the event type.
 */
@Component
public class RabbitEventPublisher implements EventPublisher {

    public static final String EXCHANGE = "ledger";

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbit;
    private final AmqpAdmin admin;
    private final TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);

    public RabbitEventPublisher(RabbitTemplate rabbit, AmqpAdmin admin) {
        this.rabbit = rabbit;
        this.admin = admin;
    }

    @Override
    public void publish(List<OutboxMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        admin.declareExchange(exchange);
        rabbit.invoke(operations -> {
            for (var message : messages) {
                operations.send(EXCHANGE, message.eventType(), amqpMessage(message));
            }
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    private static Message amqpMessage(OutboxMessage message) {
        return MessageBuilder.withBody(message.body().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(message.eventId().toString())
                .setType(message.eventType())
                .build();
    }
}
