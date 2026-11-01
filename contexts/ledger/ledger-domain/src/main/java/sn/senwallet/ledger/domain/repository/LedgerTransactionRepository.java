package sn.senwallet.ledger.domain.repository;

import java.util.Map;
import java.util.Optional;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.RequestHash;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;

/**
 * Writes follow the posting algorithm: the header is inserted first (the
 * idempotency gate), then the entries once the balances are checked.
 */
public interface LedgerTransactionRepository {

    /**
     * Inserts the transaction header unless the ledger already holds its external
     * id; returns {@code false} in that case and writes nothing. Concurrent
     * callers with the same external id wait for the first one to finish. Throws
     * {@link sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException}
     * if the transaction is a reversal of an already reversed transaction.
     */
    boolean insertIfAbsent(LedgerTransaction transaction, RequestHash requestHash);

    /**
     * Inserts the entries of a transaction whose header was inserted; each entry
     * records the lock version of its account after the posting.
     */
    void insertEntries(LedgerTransaction transaction, Map<AccountId, Long> accountVersions);

    /** Persists a status change out of {@code PENDING}. */
    void updateStatus(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(TransactionId id);

    /**
     * Loads and locks a transaction until the surrounding transaction ends, so
     * two concurrent transitions of the same transaction run one after the other.
     */
    Optional<LedgerTransaction> findByIdForUpdate(TransactionId id);

    Optional<StoredTransaction> findByExternalId(LedgerId ledgerId, ExternalId externalId);

    record StoredTransaction(LedgerTransaction transaction, RequestHash requestHash) {
    }
}
