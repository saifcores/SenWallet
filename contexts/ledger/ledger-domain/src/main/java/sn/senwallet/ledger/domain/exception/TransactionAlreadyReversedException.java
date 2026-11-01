package sn.senwallet.ledger.domain.exception;

import sn.senwallet.shared.id.TransactionId;

/** A transaction is reversed at most once. */
public final class TransactionAlreadyReversedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient TransactionId transactionId;

    public TransactionAlreadyReversedException(TransactionId transactionId) {
        super("transaction " + transactionId + " is already reversed");
        this.transactionId = transactionId;
    }

    public TransactionId transactionId() {
        return transactionId;
    }
}
