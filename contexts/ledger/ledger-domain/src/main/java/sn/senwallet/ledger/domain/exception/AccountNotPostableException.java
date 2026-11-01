package sn.senwallet.ledger.domain.exception;

import sn.senwallet.shared.id.AccountId;

/** An entry or condition targets an account that is missing or inactive. */
public final class AccountNotPostableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AccountId accountId;

    public AccountNotPostableException(AccountId accountId, String reason) {
        super("account " + accountId + " cannot be posted to: " + reason);
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
