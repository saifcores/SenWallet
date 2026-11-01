package sn.senwallet.ledger.domain.model.condition;

public enum Operator {
    GT(">"),
    GTE(">="),
    EQ("="),
    LTE("<="),
    LT("<");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    /** Tests the result of {@code balance.compareTo(threshold)}. */
    public boolean test(int comparison) {
        return switch (this) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case EQ -> comparison == 0;
            case LTE -> comparison <= 0;
            case LT -> comparison < 0;
        };
    }

    public String symbol() {
        return symbol;
    }
}
