package sn.senwallet.ledger.domain.model.transaction;

public enum Direction {
    DEBIT,
    CREDIT;

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
