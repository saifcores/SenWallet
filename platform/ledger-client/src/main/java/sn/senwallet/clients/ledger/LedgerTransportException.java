package sn.senwallet.clients.ledger;

/** The ledger could not be reached, or answered 5xx. Callers may retry a post. */
public final class LedgerTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LedgerTransportException(String message, Throwable cause) {
        super(message, cause);
    }

    public LedgerTransportException(String message) {
        super(message);
    }
}
