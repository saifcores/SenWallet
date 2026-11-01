package sn.senwallet.ledger.domain.repository;

import sn.senwallet.ledger.domain.model.transaction.ExternalId;

public final class DuplicateExternalIdException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ExternalId externalId;

    public DuplicateExternalIdException(ExternalId externalId) {
        super("external id already used: " + externalId);
        this.externalId = externalId;
    }

    public ExternalId externalId() {
        return externalId;
    }
}
