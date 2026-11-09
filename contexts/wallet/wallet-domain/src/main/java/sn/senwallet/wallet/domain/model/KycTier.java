package sn.senwallet.wallet.domain.model;

/** Simulated KYC level. Caps live in dated {@link LimitPolicy} rows, not here. */
public enum KycTier {
    N1,
    N2,
    MERCHANT
}
