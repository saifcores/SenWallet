package sn.senwallet.wallet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import sn.senwallet.shared.money.Money;

/** P2P fee rate in force from {@code validFrom}. {@code 0.01} is 1 percent. */
public record FeeSchedule(BigDecimal rate, Instant validFrom) {

    public FeeSchedule {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(validFrom, "validFrom");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("fee rate must not be negative");
        }
    }

    /** Latest schedule whose {@code validFrom} is not after {@code at}. */
    public static FeeSchedule effective(Collection<FeeSchedule> schedules, Instant at) {
        Objects.requireNonNull(schedules, "schedules");
        Objects.requireNonNull(at, "at");
        return schedules.stream()
                .filter(schedule -> !schedule.validFrom.isAfter(at))
                .max((left, right) -> left.validFrom.compareTo(right.validFrom))
                .orElseThrow(() -> new IllegalArgumentException("no fee schedule at " + at));
    }

    /** {@code amount * rate}, half-up to the currency exponent. A zero fee is allowed. */
    public Money feeFor(Money amount) {
        Objects.requireNonNull(amount, "amount");
        var fee = amount.amount().multiply(rate).setScale(amount.currency().exponent(), RoundingMode.HALF_UP);
        return Money.of(fee, amount.currency());
    }
}
