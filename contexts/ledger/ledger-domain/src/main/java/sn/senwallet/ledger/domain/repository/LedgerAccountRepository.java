package sn.senwallet.ledger.domain.repository;

import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.shared.id.AccountId;

public interface LedgerAccountRepository {

    Optional<LedgerAccount> findById(AccountId id);

    /**
     * Loads the given accounts in ascending id order (the lock order); missing
     * accounts are simply absent from the result.
     */
    Map<AccountId, LedgerAccount> findAllByIdsSorted(SortedSet<AccountId> ids);

    void save(LedgerAccount account);
}
