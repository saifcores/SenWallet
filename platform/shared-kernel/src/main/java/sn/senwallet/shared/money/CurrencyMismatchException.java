package sn.senwallet.shared.money;

public final class CurrencyMismatchException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("currency mismatch: expected " + expected + ", got " + actual);
    }
}
