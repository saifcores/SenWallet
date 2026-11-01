package sn.senwallet.ledger.infrastructure.adapter.out.persistence;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senwallet.ledger.domain.model.account.AccountStatus;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;

@Repository
public class JdbcLedgerAccountRepository implements LedgerAccountRepository {

    private static final String SELECT_ACCOUNT = """
            SELECT a.id, a.ledger_id, a.name, a.normal_balance, a.currency, a.balance_mode, a.status, a.metadata,
                   b.posted_dr, b.posted_cr, b.pending_dr, b.pending_cr, b.lock_version
            FROM ledger_accounts a JOIN account_balances b ON b.account_id = a.id
            """;

    private final JdbcClient jdbc;

    public JdbcLedgerAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LedgerAccount> findById(AccountId id) {
        return jdbc.sql(SELECT_ACCOUNT + "WHERE a.id = :id")
                .param("id", id.value())
                .query((rs, row) -> account(rs))
                .optional();
    }

    @Override
    public Map<AccountId, LedgerAccount> lockForUpdateSorted(SortedSet<AccountId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql(SELECT_ACCOUNT + "WHERE a.id IN (:ids) ORDER BY a.id FOR UPDATE OF b")
                .param("ids", ids.stream().map(AccountId::value).toList())
                .query((rs, row) -> account(rs))
                .stream()
                .collect(toMap(LedgerAccount::id, identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public void insert(LedgerAccount account) {
        jdbc.sql("""
                INSERT INTO ledger_accounts
                    (id, ledger_id, name, normal_balance, currency, currency_exponent, balance_mode, status, metadata)
                VALUES (:id, :ledgerId, :name, CAST(:normalBalance AS normal_balance), :currency, :exponent,
                        :balanceMode, :status, CAST(:metadata AS jsonb))
                """)
                .param("id", account.id().value())
                .param("ledgerId", account.ledgerId().value())
                .param("name", account.name())
                .param("normalBalance", Sql.label(account.normalBalance()))
                .param("currency", account.currency().name())
                .param("exponent", account.currency().exponent())
                .param("balanceMode", Sql.label(account.balanceMode()))
                .param("status", Sql.label(account.status()))
                .param("metadata", Sql.json(account.metadata()))
                .update();
        var balances = account.balances();
        jdbc.sql("""
                INSERT INTO account_balances (account_id, posted_dr, posted_cr, pending_dr, pending_cr, lock_version)
                VALUES (:id, :postedDr, :postedCr, :pendingDr, :pendingCr, :lockVersion)
                """)
                .param("id", account.id().value())
                .param("postedDr", Sql.minorUnits(balances.postedDebits()))
                .param("postedCr", Sql.minorUnits(balances.postedCredits()))
                .param("pendingDr", Sql.minorUnits(balances.pendingDebits()))
                .param("pendingCr", Sql.minorUnits(balances.pendingCredits()))
                .param("lockVersion", balances.lockVersion())
                .update();
    }

    /**
     * Each account was bumped exactly one version since it was locked; any other
     * stored version means the row was not locked by this transaction.
     */
    @Override
    public void saveBalances(Collection<LedgerAccount> accounts) {
        for (var account : accounts) {
            var balances = account.balances();
            var updated = jdbc.sql("""
                    UPDATE account_balances
                    SET posted_dr = :postedDr, posted_cr = :postedCr, pending_dr = :pendingDr,
                        pending_cr = :pendingCr, lock_version = :lockVersion, updated_at = now()
                    WHERE account_id = :id AND lock_version = :lockVersion - 1
                    """)
                    .param("id", account.id().value())
                    .param("postedDr", Sql.minorUnits(balances.postedDebits()))
                    .param("postedCr", Sql.minorUnits(balances.postedCredits()))
                    .param("pendingDr", Sql.minorUnits(balances.pendingDebits()))
                    .param("pendingCr", Sql.minorUnits(balances.pendingCredits()))
                    .param("lockVersion", balances.lockVersion())
                    .update();
            if (updated != 1) {
                throw new IllegalStateException("account " + account.id() + " changed since it was locked");
            }
        }
    }

    private static LedgerAccount account(ResultSet rs) throws SQLException {
        var currency = Sql.currency(rs, "currency");
        return LedgerAccount.restore(
                new AccountId(rs.getObject("id", UUID.class)),
                new LedgerId(rs.getObject("ledger_id", UUID.class)),
                rs.getString("name"),
                Sql.fromLabel(NormalBalance.class, rs.getString("normal_balance")),
                currency,
                Sql.fromLabel(BalanceMode.class, rs.getString("balance_mode")),
                Sql.fromLabel(AccountStatus.class, rs.getString("status")),
                Sql.metadata(rs, "metadata"),
                Sql.balances(rs, currency, rs.getLong("lock_version")));
    }
}
