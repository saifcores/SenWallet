package sn.senwallet.ledger.application.command;

import java.util.Objects;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.TransactionId;

/** Moves a pending transaction to {@code POSTED} or {@code ARCHIVED}. */
public record TransitionCommand(TransactionId transactionId, TransactionStatus target) {

    public TransitionCommand {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(target, "target");
        if (target == TransactionStatus.PENDING) {
            throw new IllegalArgumentException("a transaction can only move to POSTED or ARCHIVED");
        }
    }
}
