package sn.senwallet.ledger.domain.repository.inmemory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.repository.DuplicateExternalIdException;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.shared.id.TransactionId;

public final class InMemoryLedgerTransactionRepository implements LedgerTransactionRepository {

    private final Map<TransactionId, LedgerTransaction> byId = new ConcurrentHashMap<>();
    private final Map<ExternalId, TransactionId> byExternalId = new ConcurrentHashMap<>();

    @Override
    public Optional<LedgerTransaction> findById(TransactionId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<LedgerTransaction> findByExternalId(ExternalId externalId) {
        return Optional.ofNullable(byExternalId.get(externalId)).map(byId::get);
    }

    @Override
    public synchronized void save(LedgerTransaction transaction) {
        var owner = byExternalId.putIfAbsent(transaction.externalId(), transaction.id());
        if (owner != null && !owner.equals(transaction.id())) {
            throw new DuplicateExternalIdException(transaction.externalId());
        }
        byId.put(transaction.id(), transaction);
    }

    public List<LedgerTransaction> findAll() {
        return List.copyOf(byId.values());
    }
}
