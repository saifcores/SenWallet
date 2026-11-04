package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.RequestHash;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;

@Repository
public class JdbcLedgerTransactionRepository implements LedgerTransactionRepository {

    private static final String SELECT_HEADER = """
            SELECT id, ledger_id, external_id, request_hash, status, description, effective_at, posted_at,
                   archived_at, reverses_id, metadata
            FROM ledger_transactions
            """;

    private static final String REVERSES_ID_KEY = "ledger_transactions_reverses_id_key";

    private final JdbcClient jdbc;

    public JdbcLedgerTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(LedgerTransaction txn, RequestHash requestHash) {
        try {
            return jdbc.sql("""
                    INSERT INTO ledger_transactions (id, ledger_id, external_id, request_hash, status, description,
                                                     effective_at, posted_at, archived_at, reverses_id, metadata)
                    VALUES (:id, :ledgerId, :externalId, :requestHash, CAST(:status AS txn_status), :description,
                            :effectiveAt, :postedAt, :archivedAt, :reversesId, CAST(:metadata AS jsonb))
                    ON CONFLICT (ledger_id, external_id) DO NOTHING
                    """)
                    .param("id", txn.id().value())
                    .param("ledgerId", txn.ledgerId().value())
                    .param("externalId", txn.externalId().value())
                    .param("requestHash", requestHash.bytes())
                    .param("status", Sql.label(txn.status()))
                    .param("description", txn.description().orElse(null))
                    .param("effectiveAt", Sql.timestamp(txn.effectiveAt()))
                    .param("postedAt", Sql.timestamp(txn.postedAt().orElse(null)))
                    .param("archivedAt", Sql.timestamp(txn.archivedAt().orElse(null)))
                    .param("reversesId", txn.reversesId().map(TransactionId::value).orElse(null))
                    .param("metadata", Sql.json(txn.metadata()))
                    .update() == 1;
        } catch (DuplicateKeyException e) {
            if (txn.reversesId().isPresent() && String.valueOf(e.getMessage()).contains(REVERSES_ID_KEY)) {
                throw new TransactionAlreadyReversedException(txn.reversesId().get());
            }
            throw e;
        }
    }

    @Override
    public void insertEntries(LedgerTransaction txn, Map<AccountId, Long> accountVersions) {
        var values = new ArrayList<String>();
        var statement = new StringBuilder("""
                INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, account_version)
                VALUES
                """);
        var entries = txn.entries();
        for (int i = 0; i < entries.size(); i++) {
            values.add("(:txn, :account" + i + ", CAST(:direction" + i + " AS direction), :amount" + i
                    + ", :version" + i + ")");
        }
        statement.append(String.join(",\n", values));
        var spec = jdbc.sql(statement.toString()).param("txn", txn.id().value());
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var version = accountVersions.get(entry.accountId());
            if (version == null) {
                throw new IllegalArgumentException("no lock version for account " + entry.accountId());
            }
            spec = spec.param("account" + i, entry.accountId().value())
                    .param("direction" + i, Sql.label(entry.direction()))
                    .param("amount" + i, Sql.minorUnits(entry.amount().value()))
                    .param("version" + i, version);
        }
        spec.update();
    }

    @Override
    public void updateStatus(LedgerTransaction txn) {
        var updated = jdbc.sql("""
                UPDATE ledger_transactions
                SET status = CAST(:status AS txn_status), posted_at = :postedAt, archived_at = :archivedAt
                WHERE id = :id AND status = 'pending'
                """)
                .param("id", txn.id().value())
                .param("status", Sql.label(txn.status()))
                .param("postedAt", Sql.timestamp(txn.postedAt().orElse(null)))
                .param("archivedAt", Sql.timestamp(txn.archivedAt().orElse(null)))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("transaction " + txn.id() + " is no longer pending");
        }
    }

    @Override
    public Optional<LedgerTransaction> findById(TransactionId id) {
        return header(SELECT_HEADER + "WHERE id = :id", id).map(StoredTransaction::transaction);
    }

    @Override
    public Optional<LedgerTransaction> findByIdForUpdate(TransactionId id) {
        return header(SELECT_HEADER + "WHERE id = :id FOR UPDATE", id).map(StoredTransaction::transaction);
    }

    @Override
    public Optional<StoredTransaction> findByExternalId(LedgerId ledgerId, ExternalId externalId) {
        return jdbc.sql(SELECT_HEADER + "WHERE ledger_id = :ledgerId AND external_id = :externalId")
                .param("ledgerId", ledgerId.value())
                .param("externalId", externalId.value())
                .query((rs, row) -> Header.of(rs))
                .optional()
                .map(this::withEntries);
    }

    private Optional<StoredTransaction> header(String sql, TransactionId id) {
        return jdbc.sql(sql)
                .param("id", id.value())
                .query((rs, row) -> Header.of(rs))
                .optional()
                .map(this::withEntries);
    }

    private StoredTransaction withEntries(Header header) {
        List<Entry> entries = jdbc.sql("""
                SELECT e.account_id, e.direction, e.amount, a.currency
                FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
                WHERE e.transaction_id = :id
                ORDER BY e.id
                """)
                .param("id", header.id().value())
                .query((rs, row) -> new Entry(
                        new AccountId(rs.getObject("account_id", UUID.class)),
                        Sql.fromLabel(Direction.class, rs.getString("direction")),
                        Amount.of(Sql.money(rs.getLong("amount"), Sql.currency(rs, "currency")))))
                .list();
        return new StoredTransaction(header.restore(entries), header.requestHash());
    }

    private record Header(
            TransactionId id,
            LedgerId ledgerId,
            ExternalId externalId,
            RequestHash requestHash,
            TransactionStatus status,
            String description,
            Instant effectiveAt,
            Instant postedAt,
            Instant archivedAt,
            TransactionId reversesId,
            Map<String, String> metadata) {

        static Header of(ResultSet rs) throws SQLException {
            var reversesId = rs.getObject("reverses_id", UUID.class);
            return new Header(
                    new TransactionId(rs.getObject("id", UUID.class)),
                    new LedgerId(rs.getObject("ledger_id", UUID.class)),
                    ExternalId.of(rs.getString("external_id")),
                    RequestHash.of(rs.getBytes("request_hash")),
                    Sql.fromLabel(TransactionStatus.class, rs.getString("status")),
                    rs.getString("description"),
                    Sql.instant(rs, "effective_at"),
                    Sql.instant(rs, "posted_at"),
                    Sql.instant(rs, "archived_at"),
                    reversesId == null ? null : new TransactionId(reversesId),
                    Sql.metadata(rs, "metadata"));
        }

        LedgerTransaction restore(List<Entry> entries) {
            return LedgerTransaction.restore(id, ledgerId, externalId, entries, description, metadata, reversesId,
                    status, effectiveAt, postedAt, archivedAt);
        }
    }
}
