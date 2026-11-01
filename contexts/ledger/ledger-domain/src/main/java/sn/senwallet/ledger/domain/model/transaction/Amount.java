package sn.senwallet.ledger.domain.model.transaction;

import java.util.Objects;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

/**
 * The strictly positive amount of a ledger entry; the side is carried by
 * {@link Direction} (ADR-17).
 */
public record Amount(Money value) {

    public Amount {
        Objects.requireNonNull(value, "value");
        if (!value.isPositive()) {
            throw new IllegalArgumentException("amount must be > 0, got " + value);
        }
    }

    public static Amount of(Money value) {
        return new Amount(value);
    }

    public static Amount xof(long value) {
        return new Amount(Money.xof(value));
    }

    public Currency currency() {
        return value.currency();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
