package sn.senwallet.ledger.domain.model.account;

import java.util.Objects;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.CurrencyMismatchException;
import sn.senwallet.shared.money.Money;

/**
 * A ledger account and its counters. Its currency and normal balance never
 * change.
 */
public final class LedgerAccount {

    private final AccountId id;
    private final String name;
    private final NormalBalance normalBalance;
    private final Currency currency;
    private final BalanceMode balanceMode;
    private final AccountStatus status;
    private Balances balances;

    private LedgerAccount(
            AccountId id,
            String name,
            NormalBalance normalBalance,
            Currency currency,
            BalanceMode balanceMode,
            AccountStatus status,
            Balances balances) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.normalBalance = Objects.requireNonNull(normalBalance, "normalBalance");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balanceMode = Objects.requireNonNull(balanceMode, "balanceMode");
        this.status = Objects.requireNonNull(status, "status");
        this.balances = Objects.requireNonNull(balances, "balances");
        if (name.isBlank()) {
            throw new IllegalArgumentException("account name must not be blank");
        }
        if (balances.currency() != currency) {
            throw new CurrencyMismatchException(currency, balances.currency());
        }
    }

    public static LedgerAccount open(
            AccountId id, String name, NormalBalance normalBalance, Currency currency, BalanceMode balanceMode) {
        return new LedgerAccount(
                id, name, normalBalance, currency, balanceMode, AccountStatus.ACTIVE, Balances.zero(currency));
    }

    /** Rebuilds an account from storage. */
    public static LedgerAccount restore(
            AccountId id,
            String name,
            NormalBalance normalBalance,
            Currency currency,
            BalanceMode balanceMode,
            AccountStatus status,
            Balances balances) {
        return new LedgerAccount(id, name, normalBalance, currency, balanceMode, status, balances);
    }

    /**
     * Replaces the counters with balances projected from the current ones and
     * bumps the lock version; projections from a stale version are refused.
     */
    public void update(Balances projected) {
        Objects.requireNonNull(projected, "projected");
        if (projected.currency() != currency) {
            throw new CurrencyMismatchException(currency, projected.currency());
        }
        if (projected.lockVersion() != balances.lockVersion()) {
            throw new IllegalStateException("stale balances for account " + id + ": version "
                    + projected.lockVersion() + ", current " + balances.lockVersion());
        }
        balances = projected.nextVersion();
    }

    public Money posted() {
        return balances.posted(normalBalance);
    }

    public Money pending() {
        return balances.pending(normalBalance);
    }

    public Money available() {
        return balances.available(normalBalance);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public AccountId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public NormalBalance normalBalance() {
        return normalBalance;
    }

    public Currency currency() {
        return currency;
    }

    public BalanceMode balanceMode() {
        return balanceMode;
    }

    public AccountStatus status() {
        return status;
    }

    public Balances balances() {
        return balances;
    }

    @Override
    public String toString() {
        return "LedgerAccount[" + name + " " + id + "]";
    }
}
