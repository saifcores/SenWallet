package sn.senwallet.clients.ledger;

/** The ledger refused the call. Retrying would not change the answer. */
public final class LedgerRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public LedgerRejectedException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code == null || code.isBlank() ? "unknown" : code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
