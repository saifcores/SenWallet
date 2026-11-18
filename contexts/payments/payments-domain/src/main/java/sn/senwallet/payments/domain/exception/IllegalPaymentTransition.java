package sn.senwallet.payments.domain.exception;

import sn.senwallet.payments.domain.model.PaymentOrder;

/** A saga step tried to move backwards or skip a required state. */
public final class IllegalPaymentTransition extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalPaymentTransition(PaymentOrder.State from, String action) {
        super("cannot " + action + " from " + from);
    }
}
