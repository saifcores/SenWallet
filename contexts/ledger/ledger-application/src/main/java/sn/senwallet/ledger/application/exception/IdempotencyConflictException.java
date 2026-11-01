package sn.senwallet.ledger.application.exception;

import sn.senwallet.ledger.domain.model.transaction.ExternalId;

/** The external id is already used by a transaction created from a different request. */
public final class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ExternalId externalId;

    public IdempotencyConflictException(ExternalId externalId) {
        super("external id " + externalId + " was already used with a different request");
        this.externalId = externalId;
    }

    public ExternalId externalId() {
        return externalId;
    }
}
