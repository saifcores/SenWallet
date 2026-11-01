package sn.senwallet.ledger.domain.model.transaction;

import java.util.Objects;
import sn.senwallet.shared.id.AccountId;

public record Entry(AccountId accountId, Direction direction, Amount amount) {

    public Entry {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
    }

    public static Entry debit(AccountId accountId, Amount amount) {
        return new Entry(accountId, Direction.DEBIT, amount);
    }

    public static Entry credit(AccountId accountId, Amount amount) {
        return new Entry(accountId, Direction.CREDIT, amount);
    }

    public boolean isDebit() {
        return direction == Direction.DEBIT;
    }

    public boolean isCredit() {
        return direction == Direction.CREDIT;
    }
}
