package sn.senwallet.events;

import java.util.Objects;
import java.util.UUID;

public record KycTierChangedPayload(UUID walletId, String tier, long holdingCap) {

    public KycTierChangedPayload {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(tier, "tier");
        if (tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        if (holdingCap < 0) {
            throw new IllegalArgumentException("holdingCap must not be negative");
        }
    }
}
