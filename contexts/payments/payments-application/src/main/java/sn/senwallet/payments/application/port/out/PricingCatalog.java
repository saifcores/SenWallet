package sn.senwallet.payments.application.port.out;

import java.time.Instant;

public interface PricingCatalog {

    long monthlyTopupCap(Instant at);

    /** Cash-out fee in XOF for {@code amount}. Zero is allowed. */
    long cashoutFee(long amount, Instant at);
}
