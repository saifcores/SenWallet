package sn.senwallet.ledger.domain.model.account;

import java.util.Objects;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.CurrencyMismatchException;
import sn.senwallet.shared.money.Money;

/**
 * The four counters of an account ({@code account_balances}). Each bucket only
 * ever accumulates entry amounts, so it is never negative; balances are derived
 * from them according to the account's {@link NormalBalance}.
 */
public record Balances(
        Money postedDebits,
        Money postedCredits,
        Money pendingDebits,
        Money pendingCredits,
        long lockVersion) {

    public Balances {
        Objects.requireNonNull(postedDebits, "postedDebits");
        Objects.requireNonNull(postedCredits, "postedCredits");
        Objects.requireNonNull(pendingDebits, "pendingDebits");
        Objects.requireNonNull(pendingCredits, "pendingCredits");
        var currency = postedDebits.currency();
        for (var bucket : new Money[] { postedCredits, pendingDebits, pendingCredits }) {
            if (bucket.currency() != currency) {
                throw new CurrencyMismatchException(currency, bucket.currency());
            }
        }
        for (var bucket : new Money[] { postedDebits, postedCredits, pendingDebits, pendingCredits }) {
            if (bucket.isNegative()) {
                throw new IllegalArgumentException("balance bucket must be >= 0, got " + bucket);
            }
        }
        if (lockVersion < 0) {
            throw new IllegalArgumentException("lockVersion must be >= 0, got " + lockVersion);
        }
    }

    public static Balances zero(Currency currency) {
        var zero = Money.zero(currency);
        return new Balances(zero, zero, zero, zero, 0);
    }

    public Currency currency() {
        return postedDebits.currency();
    }

    /** Settled entries only. */
    public Money posted(NormalBalance normal) {
        return normal == NormalBalance.CREDIT
                ? postedCredits.minus(postedDebits)
                : postedDebits.minus(postedCredits);
    }

    /** Settled plus in-flight entries, in both directions. */
    public Money pending(NormalBalance normal) {
        var debits = postedDebits.plus(pendingDebits);
        var credits = postedCredits.plus(pendingCredits);
        return normal == NormalBalance.CREDIT ? credits.minus(debits) : debits.minus(credits);
    }

    /**
     * Settled minus in-flight outflows; in-flight inflows are not spendable yet.
     */
    public Money available(NormalBalance normal) {
        return normal == NormalBalance.CREDIT
                ? postedCredits.minus(postedDebits.plus(pendingDebits))
                : postedDebits.minus(postedCredits.plus(pendingCredits));
    }

    public Balances withPosted(Direction direction, Amount amount) {
        var value = amount.value();
        return direction == Direction.DEBIT
                ? new Balances(postedDebits.plus(value), postedCredits, pendingDebits, pendingCredits, lockVersion)
                : new Balances(postedDebits, postedCredits.plus(value), pendingDebits, pendingCredits, lockVersion);
    }

    public Balances withPending(Direction direction, Amount amount) {
        var value = amount.value();
        return direction == Direction.DEBIT
                ? new Balances(postedDebits, postedCredits, pendingDebits.plus(value), pendingCredits, lockVersion)
                : new Balances(postedDebits, postedCredits, pendingDebits, pendingCredits.plus(value), lockVersion);
    }

    /**
     * Moves an amount from the pending bucket to the posted bucket of the same
     * direction.
     */
    public Balances settled(Direction direction, Amount amount) {
        return released(direction, amount).withPosted(direction, amount);
    }

    /**
     * Removes an amount from the pending bucket; throws if the bucket does not hold
     * it.
     */
    public Balances released(Direction direction, Amount amount) {
        var value = amount.value();
        return direction == Direction.DEBIT
                ? new Balances(postedDebits, postedCredits, pendingDebits.minus(value), pendingCredits, lockVersion)
                : new Balances(postedDebits, postedCredits, pendingDebits, pendingCredits.minus(value), lockVersion);
    }

    public Balances nextVersion() {
        return new Balances(postedDebits, postedCredits, pendingDebits, pendingCredits, lockVersion + 1);
    }
}
