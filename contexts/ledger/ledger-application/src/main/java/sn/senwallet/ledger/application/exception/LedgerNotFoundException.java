package sn.senwallet.ledger.application.exception;

import sn.senwallet.shared.id.LedgerId;

public final class LedgerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient LedgerId id;

    public LedgerNotFoundException(LedgerId id) {
        super("ledger not found: " + id);
        this.id = id;
    }

    public LedgerId id() {
        return id;
    }
}
