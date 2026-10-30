package sn.senwallet.shared.money;

/**
 * Currencies handled by the platform. Amounts are always stored as integers in
 * the currency's minor unit; for XOF the exponent is 0 (5 000 XOF = 5000).
 */
public enum Currency {
    XOF(0);

    private final int exponent;

    Currency(int exponent) {
        this.exponent = exponent;
    }

    public int exponent() {
        return exponent;
    }
}
