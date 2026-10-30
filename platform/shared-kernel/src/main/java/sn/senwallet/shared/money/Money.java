package sn.senwallet.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A signed amount in a given currency. The scale is always the currency's
 * exponent (0 for XOF): fractional francs are rejected, never rounded, and two
 * equal amounts are always {@code equals}.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            amount = amount.setScale(currency.exponent());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    amount.toPlainString() + " has more decimals than " + currency + " allows", e);
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money xof(long amount) {
        return new Money(BigDecimal.valueOf(amount), Currency.XOF);
    }

    public static Money xof(BigDecimal amount) {
        return new Money(amount, Currency.XOF);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /** Amount in minor units, as stored in BIGINT columns; throws if it does not fit in a long. */
    public long toMinorUnits() {
        return amount.movePointRight(currency.exponent()).longValueExact();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
