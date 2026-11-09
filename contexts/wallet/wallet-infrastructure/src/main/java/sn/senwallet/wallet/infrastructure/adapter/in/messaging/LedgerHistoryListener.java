package sn.senwallet.wallet.infrastructure.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import sn.senwallet.events.LedgerEventTypes;
import sn.senwallet.events.inbox.IdempotentInbox;
import sn.senwallet.wallet.application.model.LedgerFact;
import sn.senwallet.wallet.application.service.RecordLedgerEventHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies each {@code ledger.transaction.*} once. The claim and the history
 * insert share one transaction, so a handler failure is delivered again.
 */
@Component
public class LedgerHistoryListener {

    public static final String QUEUE = "wallet.ledger";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IdempotentInbox inbox;
    private final RecordLedgerEventHandler recorder;
    private final TransactionTemplate transactions;

    public LedgerHistoryListener(
            JdbcClient jdbc,
            RecordLedgerEventHandler recorder,
            TransactionTemplate transactions) {
        this.inbox = new IdempotentInbox(jdbc);
        this.recorder = recorder;
        this.transactions = transactions;
    }

    @RabbitListener(queues = QUEUE)
    public void onEvent(Message message) {
        var fact = fact(JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8)));
        transactions.executeWithoutResult(status -> {
            if (!LedgerEventTypes.ALL.contains(fact.eventType())) {
                inbox.claim(fact.eventId(), () -> {
                });
                return;
            }
            inbox.claim(fact.eventId(), () -> recorder.record(fact));
        });
    }

    private static LedgerFact fact(JsonNode tree) {
        var payload = tree.path("payload");
        return new LedgerFact(
                UUID.fromString(tree.path("event_id").asString()),
                tree.path("event_type").asString(),
                Instant.parse(tree.path("occurred_at").asString()),
                UUID.fromString(payload.path("transaction_id").asString()),
                payload.path("external_id").asString(),
                UUID.fromString(payload.path("ledger_id").asString()));
    }
}
