package sn.senwallet.ledger.domain.model.transaction;

import java.util.Objects;

/**
 * Caller-supplied, deterministic idempotency key, unique per ledger (e.g.
 * {@code p2p:<transfer_id>}).
 */
public record ExternalId(String value) {

    public ExternalId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("external id must not be blank");
        }
    }

    public static ExternalId of(String value) {
        return new ExternalId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
