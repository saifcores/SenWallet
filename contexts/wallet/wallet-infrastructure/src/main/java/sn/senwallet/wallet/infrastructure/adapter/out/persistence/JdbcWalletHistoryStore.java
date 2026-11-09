package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.model.HistoryEntry;
import sn.senwallet.wallet.application.port.out.WalletHistoryStore;

@Repository
public class JdbcWalletHistoryStore implements WalletHistoryStore {

    private final JdbcClient jdbc;

    public JdbcWalletHistoryStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(HistoryEntry entry) {
        jdbc.sql("""
                INSERT INTO wallet_history (
                    event_id, event_type, wallet_id, transaction_id, external_id, ledger_id,
                    direction, amount, occurred_at)
                VALUES (
                    :eventId, :eventType, :walletId, :transactionId, :externalId, :ledgerId,
                    :direction, :amount, :occurredAt)
                ON CONFLICT (event_id, wallet_id) DO NOTHING
                """)
                .param("eventId", entry.eventId())
                .param("eventType", entry.eventType())
                .param("walletId", entry.walletId().value())
                .param("transactionId", entry.transactionId())
                .param("externalId", entry.externalId())
                .param("ledgerId", entry.ledgerId())
                .param("direction", entry.direction())
                .param("amount", entry.amount())
                .param("occurredAt", Columns.timestamp(entry.occurredAt()))
                .update();
    }

    @Override
    public List<HistoryEntry> findByWallet(WalletId walletId) {
        return jdbc.sql("""
                SELECT event_id, event_type, wallet_id, transaction_id, external_id, ledger_id,
                       direction, amount, occurred_at
                FROM wallet_history
                WHERE wallet_id = :walletId
                ORDER BY occurred_at, id
                """)
                .param("walletId", walletId.value())
                .query((rs, row) -> new HistoryEntry(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        new WalletId(rs.getObject("wallet_id", UUID.class)),
                        rs.getObject("transaction_id", UUID.class),
                        rs.getString("external_id"),
                        rs.getObject("ledger_id", UUID.class),
                        rs.getString("direction"),
                        rs.getLong("amount"),
                        Columns.instant(rs, "occurred_at")))
                .list();
    }
}
