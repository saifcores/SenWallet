package sn.senwallet.ledger.infrastructure.adapter.in.web;

/** A request body or parameter that cannot be turned into a command (400). */
public final class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(field + ": " + message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
