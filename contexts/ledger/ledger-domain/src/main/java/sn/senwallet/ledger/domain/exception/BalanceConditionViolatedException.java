package sn.senwallet.ledger.domain.exception;

import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.shared.money.Money;

/**
 * A balance condition is false once the transaction is applied; nothing was
 * changed.
 */
public final class BalanceConditionViolatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient BalanceCondition condition;
    private final transient Money actual;

    public BalanceConditionViolatedException(BalanceCondition condition, Money actual) {
        super("balance condition violated: " + condition + " (would be " + actual + ")");
        this.condition = condition;
        this.actual = actual;
    }

    public BalanceCondition condition() {
        return condition;
    }

    public Money actual() {
        return actual;
    }
}
