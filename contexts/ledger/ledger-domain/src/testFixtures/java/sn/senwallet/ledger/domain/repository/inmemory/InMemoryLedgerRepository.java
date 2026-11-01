package sn.senwallet.ledger.domain.repository.inmemory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.shared.id.LedgerId;

public final class InMemoryLedgerRepository implements LedgerRepository {

    private final Map<LedgerId, Ledger> ledgers = new ConcurrentHashMap<>();

    @Override
    public void insert(Ledger ledger) {
        if (ledgers.putIfAbsent(ledger.id(), ledger) != null) {
            throw new IllegalStateException("ledger already exists: " + ledger.id());
        }
    }

    @Override
    public Optional<Ledger> findById(LedgerId id) {
        return Optional.ofNullable(ledgers.get(id));
    }
}
