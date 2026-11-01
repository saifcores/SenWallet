package sn.senwallet.ledger.application.port.in;

import java.util.Objects;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.Money;

/** The three balances of an account, derived from its four counters. */
public record AccountBalances(AccountId accountId, Money posted, Money pending, Money available, long lockVersion) {

    public AccountBalances {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(posted, "posted");
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(available, "available");
    }

    public static AccountBalances of(LedgerAccount account) {
        return of(account.id(), account.normalBalance(), account.balances());
    }

    public static AccountBalances of(AccountId id, NormalBalance normal, Balances balances) {
        return new AccountBalances(id, balances.posted(normal), balances.pending(normal), balances.available(normal),
                balances.lockVersion());
    }
}
