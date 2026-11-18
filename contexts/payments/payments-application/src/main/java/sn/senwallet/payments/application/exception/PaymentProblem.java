package sn.senwallet.payments.application.exception;

/** A failure the HTTP layer renders as problem details. */
public final class PaymentProblem extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String field;

    public PaymentProblem(int status, String code, String detail) {
        this(status, code, detail, null);
    }

    public PaymentProblem(int status, String code, String detail, String field) {
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

    public static PaymentProblem notFound(String detail) {
        return new PaymentProblem(404, "not_found", detail);
    }

    public static PaymentProblem invalid(String field, String detail) {
        return new PaymentProblem(400, "invalid_request", detail, field);
    }
}
