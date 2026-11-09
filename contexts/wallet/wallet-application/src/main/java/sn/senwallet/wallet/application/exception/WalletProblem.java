package sn.senwallet.wallet.application.exception;

/**
 * A failure the HTTP layer renders as problem details. {@code field} is set
 * for {@code invalid_request} only.
 */
public final class WalletProblem extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String field;

    public WalletProblem(int status, String code, String detail) {
        this(status, code, detail, null);
    }

    public WalletProblem(int status, String code, String detail, String field) {
        super(detail);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public static WalletProblem notFound(String detail) {
        return new WalletProblem(404, "not_found", detail);
    }

    public static WalletProblem invalid(String field, String detail) {
        return new WalletProblem(400, "invalid_request", detail, field);
    }
}
