package sn.senwallet.ledger.domain.model.transaction;

/**
 * Only {@code PENDING -> POSTED} and {@code PENDING -> ARCHIVED} are allowed.
 */
public enum TransactionStatus {
    PENDING,
    POSTED,
    ARCHIVED
}
