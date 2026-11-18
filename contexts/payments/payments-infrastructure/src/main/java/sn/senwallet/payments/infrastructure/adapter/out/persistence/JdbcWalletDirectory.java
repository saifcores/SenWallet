package sn.senwallet.payments.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.payments.application.port.out.WalletDirectory;

@Repository
public class JdbcWalletDirectory implements WalletDirectory {

    private final JdbcClient jdbc;

    public JdbcWalletDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProjectedWallet> find(UUID walletId) {
        return jdbc.sql("SELECT * FROM wallet_projections WHERE wallet_id = :id")
                .param("id", walletId)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public Optional<ProjectedWallet> findByAlias(String alias) {
        return jdbc.sql("SELECT * FROM wallet_projections WHERE pispi_alias = :alias")
                .param("alias", alias)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public void upsert(ProjectedWallet wallet) {
        jdbc.sql("""
                INSERT INTO wallet_projections (
                    wallet_id, customer_id, ledger_id, ledger_account_id, pispi_alias, phone,
                    tier, holding_cap, fee_account_id, updated_at)
                VALUES (
                    :walletId, :customerId, :ledgerId, :accountId, :alias, :phone,
                    :tier, :holdingCap, :feeAccountId, now())
                ON CONFLICT (wallet_id) DO UPDATE SET
                    tier = EXCLUDED.tier,
                    holding_cap = EXCLUDED.holding_cap,
                    phone = EXCLUDED.phone,
                    pispi_alias = EXCLUDED.pispi_alias,
                    updated_at = now()
                """)
                .param("walletId", wallet.walletId())
                .param("customerId", wallet.customerId())
                .param("ledgerId", wallet.ledgerId())
                .param("accountId", wallet.ledgerAccountId())
                .param("alias", wallet.alias())
                .param("phone", wallet.phone())
                .param("tier", wallet.tier())
                .param("holdingCap", wallet.holdingCap())
                .param("feeAccountId", wallet.feeAccountId())
                .update();
    }

    private static ProjectedWallet map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProjectedWallet(
                rs.getObject("wallet_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getObject("ledger_id", UUID.class),
                rs.getObject("ledger_account_id", UUID.class),
                rs.getString("pispi_alias"),
                rs.getString("phone"),
                rs.getString("tier"),
                rs.getLong("holding_cap"),
                rs.getObject("fee_account_id", UUID.class));
    }
}
