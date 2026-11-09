package sn.senwallet.wallet.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import sn.senwallet.shared.money.Money;

/** Holding cap for one KYC tier, in force from {@code validFrom}. */
public record LimitPolicy(KycTier tier, Money holdingCap, Instant validFrom) {

    public LimitPolicy {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(holdingCap, "holdingCap");
        Objects.requireNonNull(validFrom, "validFrom");
        if (holdingCap.isNegative()) {
            throw new IllegalArgumentException("holding cap must not be negative");
        }
    }

    /** Latest policy for {@code tier} whose {@code validFrom} is not after {@code at}. */
    public static LimitPolicy effective(Collection<LimitPolicy> policies, KycTier tier, Instant at) {
        Objects.requireNonNull(policies, "policies");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(at, "at");
        return policies.stream()
                .filter(policy -> policy.tier == tier && !policy.validFrom.isAfter(at))
                .max((left, right) -> left.validFrom.compareTo(right.validFrom))
                .orElseThrow(() -> new IllegalArgumentException("no limit policy for " + tier + " at " + at));
    }
}
