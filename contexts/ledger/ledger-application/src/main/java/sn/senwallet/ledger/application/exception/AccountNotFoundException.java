package sn.senwallet.ledger.application.exception;

import sn.senwallet.shared.id.AccountId;

public final class AccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AccountId id;

    public AccountNotFoundException(AccountId id) {
        super("account not found: " + id);
        this.id = id;
    }

    public AccountId id() {
        return id;
    }
}
