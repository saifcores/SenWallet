package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.ledger.application.port.out.OutboxPort;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;

@Repository
public class JdbcOutboxAdapter implements OutboxPort {

    private final JdbcClient jdbc;

    public JdbcOutboxAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerTransaction transaction, List<LedgerTransactionEvent> events) {
        for (var event : events) {
            if (!event.transactionId().equals(transaction.id())) {
                throw new IllegalArgumentException("event is for another transaction: " + event.eventId());
            }
            jdbc.sql("""
                    INSERT INTO outbox (event_id, aggregate_type, aggregate_id, event_type, payload, created_at)
                    VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), :createdAt)
                    """)
                    .param("eventId", event.eventId())
                    .param("aggregateType", LedgerEventEnvelopes.AGGREGATE_TYPE)
                    .param("aggregateId", event.transactionId().value())
                    .param("eventType", LedgerEventEnvelopes.eventType(event))
                    .param("payload", LedgerEventEnvelopes.json(transaction, event))
                    .param("createdAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                    .update();
        }
    }
}
