package sn.senwallet.ledger.domain.repository;

import java.util.Optional;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.shared.id.TransactionId;

public interface LedgerTransactionRepository {

    Optional<LedgerTransaction> findById(TransactionId id);

    Optional<LedgerTransaction> findByExternalId(ExternalId externalId);

    /**
     * Inserts or updates a transaction; throws {@link DuplicateExternalIdException}
     * if another transaction already uses its external id.
     */
    void save(LedgerTransaction transaction);
}
