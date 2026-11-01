package sn.senwallet.ledger.domain.model.condition;

import java.util.Locale;
import java.util.Objects;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.Money;

/**
 * A check on an account's balance after the transaction is applied, e.g.
 * {@code available >= 0} on the payer or {@code pending <= 4 000 000} on the
 * payee.
 */
public record BalanceCondition(AccountId accountId, BalanceType type, Operator operator, Money threshold) {

    public BalanceCondition {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(threshold, "threshold");
    }

    public static BalanceCondition of(AccountId accountId, BalanceType type, Operator operator, Money threshold) {
        return new BalanceCondition(accountId, type, operator, threshold);
    }

    public boolean isSatisfiedBy(Balances balances, NormalBalance normal) {
        return operator.test(balanceOf(balances, normal).compareTo(threshold));
    }

    public Money balanceOf(Balances balances, NormalBalance normal) {
        return switch (type) {
            case POSTED -> balances.posted(normal);
            case PENDING -> balances.pending(normal);
            case AVAILABLE -> balances.available(normal);
        };
    }

    @Override
    public String toString() {
        return type.name().toLowerCase(Locale.ROOT) + " " + operator.symbol() + " " + threshold
                + " on " + accountId;
    }
}
