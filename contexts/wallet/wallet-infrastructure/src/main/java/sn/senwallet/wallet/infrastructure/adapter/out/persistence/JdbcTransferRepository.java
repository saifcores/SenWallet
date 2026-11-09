package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.shared.money.Money;
import sn.senwallet.wallet.domain.model.Transfer;
import sn.senwallet.wallet.domain.model.TransferId;
import sn.senwallet.wallet.domain.model.TransferStatus;
import sn.senwallet.wallet.domain.repository.TransferRepository;

@Repository
public class JdbcTransferRepository implements TransferRepository {

    private final JdbcClient jdbc;

    public JdbcTransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Transfer transfer) {
        jdbc.sql("""
                INSERT INTO transfers (
                    id, from_wallet_id, to_wallet_id, amount, fee, currency, status,
                    ledger_transaction_id, created_at)
                VALUES (
                    :id, :fromId, :toId, :amount, :fee, :currency, CAST(:status AS transfer_status),
                    :ledgerId, now())
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", transfer.id().value())
                .param("fromId", transfer.fromWalletId().value())
                .param("toId", transfer.toWalletId().value())
                .param("amount", transfer.amount().toMinorUnits())
                .param("fee", transfer.fee().toMinorUnits())
                .param("currency", transfer.amount().currency().name())
                .param("status", Columns.label(transfer.status()))
                .param("ledgerId", transfer.ledgerTransactionId())
                .update();
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return jdbc.sql("""
                SELECT id, from_wallet_id, to_wallet_id, amount, fee, status, ledger_transaction_id
                FROM transfers WHERE id = :id
                """)
                .param("id", id.value())
                .query((rs, row) -> new Transfer(
                        new TransferId(rs.getObject("id", UUID.class)),
                        new WalletId(rs.getObject("from_wallet_id", UUID.class)),
                        new WalletId(rs.getObject("to_wallet_id", UUID.class)),
                        Money.xof(rs.getLong("amount")),
                        Money.xof(rs.getLong("fee")),
                        Columns.enumOf(TransferStatus.class, rs.getString("status")),
                        rs.getObject("ledger_transaction_id", UUID.class)))
                .optional();
    }
}
