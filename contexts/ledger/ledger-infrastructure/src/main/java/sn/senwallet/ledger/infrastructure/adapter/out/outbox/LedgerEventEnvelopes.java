package sn.senwallet.ledger.infrastructure.adapter.out.outbox;

import sn.senwallet.events.EventEnvelope;
import sn.senwallet.events.LedgerEventTypes;
import sn.senwallet.events.LedgerTransactionPayload;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.event.TransactionArchived;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps a domain fact to the published envelope. {@code traceId} stays null
 * until M10.
 */
final class LedgerEventEnvelopes {

    static final String AGGREGATE_TYPE = LedgerEventTypes.AGGREGATE_TYPE;

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private LedgerEventEnvelopes() {
    }

    static String eventType(LedgerTransactionEvent event) {
        return switch (event) {
            case TransactionPending _ -> LedgerEventTypes.TRANSACTION_PENDING;
            case TransactionPosted _ -> LedgerEventTypes.TRANSACTION_POSTED;
            case TransactionArchived _ -> LedgerEventTypes.TRANSACTION_ARCHIVED;
        };
    }

    static String json(LedgerTransaction transaction, LedgerTransactionEvent event) {
        var payload = new LedgerTransactionPayload(
                event.transactionId().value(), event.externalId().value(), transaction.ledgerId().value());
        var envelope = new EventEnvelope<>(event.eventId(), eventType(event), event.occurredAt(), null,
                event.transactionId().value(), EventEnvelope.VERSION, payload);
        return JSON.writeValueAsString(envelope);
    }
}
