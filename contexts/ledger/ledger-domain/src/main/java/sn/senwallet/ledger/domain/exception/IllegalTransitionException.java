package sn.senwallet.ledger.domain.exception;

import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;

public final class IllegalTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TransactionStatus from;
    private final TransactionStatus to;

    public IllegalTransitionException(TransactionStatus from, TransactionStatus to) {
        super("illegal transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public TransactionStatus from() {
        return from;
    }

    public TransactionStatus to() {
        return to;
    }
}
