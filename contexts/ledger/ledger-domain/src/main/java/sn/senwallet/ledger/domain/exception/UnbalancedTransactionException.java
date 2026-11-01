package sn.senwallet.ledger.domain.exception;

import sn.senwallet.shared.money.Money;

/**
 * A transaction with fewer than two entries, or whose debits and credits
 * differ.
 */
public final class UnbalancedTransactionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnbalancedTransactionException(String message) {
        super(message);
    }

    public UnbalancedTransactionException(Money debits, Money credits) {
        super("debits " + debits + " != credits " + credits);
    }
}
