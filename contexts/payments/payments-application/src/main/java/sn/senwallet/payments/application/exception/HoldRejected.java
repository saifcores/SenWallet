package sn.senwallet.payments.application.exception;

/** The ledger refused the hold. Nothing was written on the ledger. */
public final class HoldRejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public HoldRejected(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
