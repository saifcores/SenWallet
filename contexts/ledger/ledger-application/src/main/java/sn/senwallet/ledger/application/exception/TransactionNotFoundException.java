package sn.senwallet.ledger.application.exception;

import sn.senwallet.shared.id.TransactionId;

public final class TransactionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient TransactionId id;

    public TransactionNotFoundException(TransactionId id) {
        super("transaction not found: " + id);
        this.id = id;
    }

    public TransactionId id() {
        return id;
    }
}
