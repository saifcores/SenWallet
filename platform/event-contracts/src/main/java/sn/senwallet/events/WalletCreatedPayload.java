package sn.senwallet.events;

import java.util.Objects;
import java.util.UUID;

/** A wallet became active. Payments projects it and opens rail accounts on {@code ledgerId}. */
public record WalletCreatedPayload(
        UUID walletId,
        UUID customerId,
        UUID ledgerId,
        UUID ledgerAccountId,
        String pispiAlias,
        String phone,
        String tier,
        long holdingCap,
        UUID feeAccountId) {

    public WalletCreatedPayload {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
        Objects.requireNonNull(pispiAlias, "pispiAlias");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(feeAccountId, "feeAccountId");
        if (holdingCap < 0) {
            throw new IllegalArgumentException("holdingCap must not be negative");
        }
    }
}
