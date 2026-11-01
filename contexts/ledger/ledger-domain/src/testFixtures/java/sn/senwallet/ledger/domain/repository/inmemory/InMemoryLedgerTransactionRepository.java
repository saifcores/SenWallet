package sn.senwallet.ledger.domain.repository.inmemory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.RequestHash;
import sn.senwallet.ledger.domain.repository.DuplicateExternalIdException;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;

/** Stores copies, like a database would; loaded transactions carry no pending events. */
public final class InMemoryLedgerTransactionRepository implements LedgerTransactionRepository {

    private static final RequestHash NO_HASH = new RequestHash("00");

    private final Map<TransactionId, StoredTransaction> byId = new ConcurrentHashMap<>();
    private final Map<Key, TransactionId> byExternalId = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean insertIfAbsent(LedgerTransaction transaction, RequestHash requestHash) {
        var key = new Key(transaction.ledgerId(), transaction.externalId());
        if (byExternalId.containsKey(key)) {
            return false;
        }
        var reverses = transaction.reversesId();
        if (reverses.isPresent() && byId.values().stream()
                .anyMatch(s -> s.transaction().reversesId().equals(reverses))) {
            throw new TransactionAlreadyReversedException(reverses.get());
        }
        byExternalId.put(key, transaction.id());
        byId.put(transaction.id(), new StoredTransaction(copy(transaction), requestHash));
        return true;
    }

    @Override
    public void insertEntries(LedgerTransaction transaction, Map<AccountId, Long> accountVersions) {
        if (!byId.containsKey(transaction.id())) {
            throw new IllegalStateException("header not inserted: " + transaction);
        }
    }

    @Override
    public synchronized void updateStatus(LedgerTransaction transaction) {
        var stored = byId.get(transaction.id());
        if (stored == null) {
            throw new IllegalStateException("unknown transaction: " + transaction);
        }
        byId.put(transaction.id(), new StoredTransaction(copy(transaction), stored.requestHash()));
    }

    @Override
    public Optional<LedgerTransaction> findById(TransactionId id) {
        return Optional.ofNullable(byId.get(id)).map(stored -> copy(stored.transaction()));
    }

    @Override
    public Optional<LedgerTransaction> findByIdForUpdate(TransactionId id) {
        return findById(id);
    }

    @Override
    public Optional<StoredTransaction> findByExternalId(LedgerId ledgerId, ExternalId externalId) {
        return Optional.ofNullable(byExternalId.get(new Key(ledgerId, externalId)))
                .map(byId::get)
                .map(stored -> new StoredTransaction(copy(stored.transaction()), stored.requestHash()));
    }

    /** Looks up an external id in any ledger; for single-ledger tests. */
    public Optional<LedgerTransaction> findByExternalId(ExternalId externalId) {
        return byExternalId.entrySet().stream()
                .filter(e -> e.getKey().externalId().equals(externalId))
                .findFirst()
                .flatMap(e -> findById(e.getValue()));
    }

    /** Inserts or updates a transaction without a request hash. */
    public synchronized void save(LedgerTransaction transaction) {
        var key = new Key(transaction.ledgerId(), transaction.externalId());
        var owner = byExternalId.putIfAbsent(key, transaction.id());
        if (owner != null && !owner.equals(transaction.id())) {
            throw new DuplicateExternalIdException(transaction.externalId());
        }
        var hash = Optional.ofNullable(byId.get(transaction.id())).map(StoredTransaction::requestHash).orElse(NO_HASH);
        byId.put(transaction.id(), new StoredTransaction(copy(transaction), hash));
    }

    public List<LedgerTransaction> findAll() {
        return byId.values().stream().map(stored -> copy(stored.transaction())).toList();
    }

    private static LedgerTransaction copy(LedgerTransaction txn) {
        return LedgerTransaction.restore(
                txn.id(),
                txn.ledgerId(),
                txn.externalId(),
                txn.entries(),
                txn.description().orElse(null),
                txn.metadata(),
                txn.reversesId().orElse(null),
                txn.status(),
                txn.effectiveAt(),
                txn.postedAt().orElse(null),
                txn.archivedAt().orElse(null));
    }

    private record Key(LedgerId ledgerId, ExternalId externalId) {
    }
}
