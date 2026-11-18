package sn.senwallet.wallet.infrastructure.adapter.out.outbox;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sn.senwallet.events.EventEnvelope;
import sn.senwallet.events.KycTierChangedPayload;
import sn.senwallet.events.WalletCreatedPayload;
import sn.senwallet.events.WalletEventTypes;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Wallet;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Appends wallet facts in the caller's transaction. */
@Component
public class JdbcWalletOutbox {

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final JdbcClient jdbc;

    public JdbcWalletOutbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void walletCreated(Customer customer, Wallet wallet, UUID ledgerId, UUID feeAccountId, long holdingCap) {
        var payload = new WalletCreatedPayload(
                wallet.id().value(),
                customer.id().value(),
                ledgerId,
                wallet.ledgerAccountId().value(),
                wallet.alias().value(),
                customer.phone().value(),
                customer.tier().name().toLowerCase(java.util.Locale.ROOT),
                holdingCap,
                feeAccountId);
        append(WalletEventTypes.CREATED, wallet.id().value(), payload);
    }

    public void tierChanged(Wallet wallet, String tier, long holdingCap) {
        var payload = new KycTierChangedPayload(wallet.id().value(), tier, holdingCap);
        append(WalletEventTypes.TIER_CHANGED, wallet.id().value(), payload);
    }

    private void append(String eventType, UUID aggregateId, Object payload) {
        var eventId = UUID.randomUUID();
        var envelope = new EventEnvelope<>(eventId, eventType, Instant.now(), null, aggregateId, EventEnvelope.VERSION,
                payload);
        jdbc.sql("""
                INSERT INTO outbox (event_id, aggregate_type, aggregate_id, event_type, payload, created_at)
                VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), :createdAt)
                """)
                .param("eventId", eventId)
                .param("aggregateType", WalletEventTypes.AGGREGATE_TYPE)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payload", JSON.writeValueAsString(envelope))
                .param("createdAt", Instant.now().atOffset(ZoneOffset.UTC))
                .update();
    }
}
