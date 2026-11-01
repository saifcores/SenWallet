package sn.senwallet.ledger.domain.repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.shared.id.AccountId;

public interface LedgerAccountRepository {

    Optional<LedgerAccount> findById(AccountId id);

    /**
     * Loads and locks the given accounts in ascending id order, so two postings
     * touching the same accounts never deadlock; missing accounts are absent from
     * the result. Locks are held until the surrounding transaction ends.
     */
    Map<AccountId, LedgerAccount> lockForUpdateSorted(SortedSet<AccountId> ids);

    /** Inserts a new account with zero balances. */
    void insert(LedgerAccount account);

    /** Writes the counters and lock version of accounts loaded by {@link #lockForUpdateSorted}. */
    void saveBalances(Collection<LedgerAccount> accounts);
}
