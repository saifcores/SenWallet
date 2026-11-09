package sn.senwallet.wallet.application.port.out;

import java.time.Instant;
import sn.senwallet.wallet.domain.model.FeeSchedule;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.model.LimitPolicy;

/** Dated caps and the P2P fee, read as they were at {@code at}. */
public interface PricingCatalog {

    LimitPolicy limit(KycTier tier, Instant at);

    FeeSchedule p2pFee(Instant at);
}
