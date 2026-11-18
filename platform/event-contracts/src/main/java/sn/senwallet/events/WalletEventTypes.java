package sn.senwallet.events;

/** Routing keys for wallet facts consumed by payments. */
public final class WalletEventTypes {

    public static final String AGGREGATE_TYPE = "wallet";
    public static final String CREATED = "wallet.created";
    public static final String TIER_CHANGED = "wallet.kyc_tier_changed";

    private WalletEventTypes() {
    }
}
