package sn.senwallet.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class EventEnvelopeTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void schemasNameTheEnvelopeAndTheThreeLedgerEvents() throws Exception {
        var envelope = schema("envelope.schema.json");
        assertThat(envelope).contains("event_id", "event_type", "occurred_at", "trace_id", "aggregate_id",
                "version", "payload");

        var ledger = schema("ledger-transaction-event.schema.json");
        assertThat(ledger).contains(LedgerEventTypes.TRANSACTION_PENDING, LedgerEventTypes.TRANSACTION_POSTED,
                LedgerEventTypes.TRANSACTION_ARCHIVED);

        var payload = schema("ledger-transaction-payload.schema.json");
        assertThat(payload).contains("transaction_id", "external_id", "ledger_id");
    }

    @Test
    void eachLedgerEventSerializesWithTheSchemaFields() throws Exception {
        for (var eventType : LedgerEventTypes.ALL) {
            var tree = JSON.readTree(JSON.writeValueAsString(sample(eventType)));

            assertThat(tree.propertyNames()).containsExactlyInAnyOrder(
                    "event_id", "event_type", "occurred_at", "trace_id", "aggregate_id", "version", "payload");
            assertThat(tree.get("event_type").asString()).isEqualTo(eventType);
            assertThat(tree.get("version").asInt()).isEqualTo(EventEnvelope.VERSION);
            assertThat(tree.get("trace_id").isNull()).isTrue();
            assertThat(tree.get("occurred_at").asString()).isEqualTo("2026-11-04T12:00:00Z");
            assertThat(tree.get("payload").propertyNames()).containsExactlyInAnyOrder(
                    "transaction_id", "external_id", "ledger_id");
        }
    }

    @Test
    void rejectsABlankTypeOrAnotherVersion() {
        assertThatThrownBy(() -> sample(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), LedgerEventTypes.TRANSACTION_POSTED,
                Instant.parse("2026-11-04T12:00:00Z"), null, UUID.randomUUID(), 2, payload()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    private static EventEnvelope<LedgerTransactionPayload> sample(String eventType) {
        var transactionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new EventEnvelope<>(UUID.fromString("22222222-2222-2222-2222-222222222222"), eventType,
                Instant.parse("2026-11-04T12:00:00Z"), null, transactionId, EventEnvelope.VERSION, payload());
    }

    private static LedgerTransactionPayload payload() {
        return new LedgerTransactionPayload(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "pispi-in:1",
                UUID.fromString("33333333-3333-3333-3333-333333333333"));
    }

    private static String schema(String name) throws Exception {
        try (var in = EventEnvelopeTest.class.getResourceAsStream("/schemas/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing schema " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
