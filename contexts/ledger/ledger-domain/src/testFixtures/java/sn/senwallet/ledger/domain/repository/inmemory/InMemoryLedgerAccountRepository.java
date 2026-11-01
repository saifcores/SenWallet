package sn.senwallet.ledger.domain.repository.inmemory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.shared.id.AccountId;

/**
 * Stores copies, like a database would: changes to a loaded account are
 * invisible until it is saved. Locking is a no-op: callers are single-threaded.
 */
public final class InMemoryLedgerAccountRepository implements LedgerAccountRepository {

    private final Map<AccountId, LedgerAccount> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<LedgerAccount> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id)).map(InMemoryLedgerAccountRepository::copy);
    }

    @Override
    public Map<AccountId, LedgerAccount> lockForUpdateSorted(SortedSet<AccountId> ids) {
        var result = new LinkedHashMap<AccountId, LedgerAccount>();
        for (var id : ids) {
            var account = accounts.get(id);
            if (account != null) {
                result.put(id, copy(account));
            }
        }
        return result;
    }

    @Override
    public void insert(LedgerAccount account) {
        if (accounts.putIfAbsent(account.id(), copy(account)) != null) {
            throw new IllegalStateException("account already exists: " + account.id());
        }
    }

    @Override
    public void saveBalances(Collection<LedgerAccount> toSave) {
        toSave.forEach(this::save);
    }

    /** Inserts or replaces an account. */
    public void save(LedgerAccount account) {
        accounts.put(account.id(), copy(account));
    }

    public void saveAll(Collection<LedgerAccount> toSave) {
        toSave.forEach(this::save);
    }

    public List<LedgerAccount> findAll() {
        return accounts.values().stream().map(InMemoryLedgerAccountRepository::copy).toList();
    }

    private static LedgerAccount copy(LedgerAccount account) {
        return LedgerAccount.restore(
                account.id(),
                account.ledgerId(),
                account.name(),
                account.normalBalance(),
                account.currency(),
                account.balanceMode(),
                account.status(),
                account.metadata(),
                account.balances());
    }
}
