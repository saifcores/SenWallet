package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.model.WalletStatus;
import sn.senwallet.wallet.domain.repository.WalletRepository;

@Repository
public class JdbcWalletRepository implements WalletRepository {

    private final JdbcClient jdbc;

    public JdbcWalletRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Wallet wallet) {
        jdbc.sql("""
                INSERT INTO wallets (id, customer_id, status, ledger_account_id, pispi_alias, created_at, updated_at)
                VALUES (:id, :customerId, CAST(:status AS wallet_status), :accountId, :alias, now(), now())
                """)
                .param("id", wallet.id().value())
                .param("customerId", wallet.customerId().value())
                .param("status", Columns.label(wallet.status()))
                .param("accountId", wallet.ledgerAccountId() == null ? null : wallet.ledgerAccountId().value())
                .param("alias", wallet.alias().value())
                .update();
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return find("id = :id", id.value());
    }

    @Override
    public Optional<Wallet> findByAlias(Phone alias) {
        return find("pispi_alias = :id", alias.value());
    }

    @Override
    public Optional<Wallet> findByCustomer(CustomerId customerId) {
        return find("customer_id = :id", customerId.value());
    }

    @Override
    public Optional<Wallet> findByLedgerAccount(UUID ledgerAccountId) {
        return find("ledger_account_id = :id", ledgerAccountId);
    }

    private Optional<Wallet> find(String where, Object id) {
        return jdbc.sql("""
                SELECT id, customer_id, status, ledger_account_id, pispi_alias
                FROM wallets WHERE %s
                """.formatted(where))
                .param("id", id)
                .query((rs, row) -> {
                    var account = rs.getObject("ledger_account_id", UUID.class);
                    return new Wallet(
                            new WalletId(rs.getObject("id", UUID.class)),
                            new CustomerId(rs.getObject("customer_id", UUID.class)),
                            Columns.enumOf(WalletStatus.class, rs.getString("status")),
                            account == null ? null : new AccountId(account),
                            new Phone(rs.getString("pispi_alias")));
                })
                .optional();
    }
}
