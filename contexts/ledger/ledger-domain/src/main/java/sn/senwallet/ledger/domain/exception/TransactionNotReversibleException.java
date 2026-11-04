package sn.senwallet.ledger.domain.exception;

import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.TransactionId;

/** Only a {@code POSTED} transaction can be reversed. */
public final class TransactionNotReversibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient TransactionId transactionId;
    private final TransactionStatus status;

    public TransactionNotReversibleException(TransactionId transactionId, TransactionStatus status) {
        super("transaction " + transactionId + " is " + status + "; only POSTED transactions can be reversed");
        this.transactionId = transactionId;
        this.status = status;
    }

    public TransactionId transactionId() {
        return transactionId;
    }

    public TransactionStatus status() {
        return status;
    }
}
