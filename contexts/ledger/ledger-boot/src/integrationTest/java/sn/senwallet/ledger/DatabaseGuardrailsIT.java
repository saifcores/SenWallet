package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.testsupport.SenwalletPostgres;

/**
 * The database refuses what the application must never do, even when the
 * application is bypassed: raw SQL as {@code ledger_app}.
 */
class DatabaseGuardrailsIT extends LedgerIntegrationTest {

    private static final String PERMISSION_DENIED = "42501";
    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    PostTransactionUseCase post;

    private LedgerId ledger;
    private AccountId treasury;
    private AccountId wallet;
    private TransactionId funding;

    @BeforeEach
    void setUp() {
        ledger = newLedger();
        treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        wallet = openAccount(ledger, "wallet", NormalBalance.CREDIT);
        funding = post.post(CountersMatchEntriesIT.command(ledger, "fund", treasury, wallet, 5_000,
                TransactionStatus.POSTED, List.of())).transaction().id();
    }

    @Test
    void entriesCannotBeUpdatedOrDeleted() throws SQLException {
        try (var db = appConnection()) {
            assertSqlState(db, "UPDATE ledger_entries SET amount = 1 WHERE transaction_id = '" + funding + "'",
                    PERMISSION_DENIED);
            assertSqlState(db, "DELETE FROM ledger_entries WHERE transaction_id = '" + funding + "'",
                    PERMISSION_DENIED);
        }
    }

    @Test
    void unbalancedEntriesFailAtCommit() throws SQLException {
        try (var db = appConnection()) {
            db.setAutoCommit(false);
            var txn = UUID.randomUUID();
            try (var statement = db.createStatement()) {
                statement.execute("""
                        INSERT INTO ledger_transactions (id, ledger_id, external_id, request_hash, status,
                                                         effective_at, posted_at)
                        VALUES ('%s', '%s', 'raw:unbalanced', '\\x00', 'posted', now(), now())
                        """.formatted(txn, ledger));
                statement.execute("""
                        INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, account_version)
                        VALUES ('%1$s', '%2$s', 'debit', 100, 99), ('%1$s', '%3$s', 'credit', 90, 99)
                        """.formatted(txn, treasury, wallet));
            }
            assertThatThrownBy(db::commit)
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(CHECK_VIOLATION))
                    .hasMessageContaining("unbalanced");
        }
    }

    @Test
    void postedTransactionsCannotGoBackToPendingNorBeEdited() throws SQLException {
        try (var db = appConnection()) {
            assertSqlState(db, "UPDATE ledger_transactions SET status = 'pending', posted_at = NULL WHERE id = '"
                    + funding + "'", CHECK_VIOLATION);
            assertSqlState(db, "UPDATE ledger_transactions SET description = 'edited' WHERE id = '" + funding + "'",
                    PERMISSION_DENIED);
            assertSqlState(db, "DELETE FROM ledger_transactions WHERE id = '" + funding + "'", PERMISSION_DENIED);
        }
    }

    @Test
    void counterBucketsCannotGoNegative() throws SQLException {
        try (var db = appConnection()) {
            assertSqlState(db, "UPDATE account_balances SET posted_cr = -1 WHERE account_id = '" + wallet + "'",
                    CHECK_VIOLATION);
        }
    }

    @Test
    void partitionsAreKeptAheadAndTheDefaultOneStaysEmpty() throws SQLException {
        try (var db = appConnection(); var statement = db.createStatement()) {
            assertThat(single(statement, "SELECT ensure_entry_partitions(3)"))
                    .as("created at startup already").isZero();
            assertThat(single(statement, """
                    SELECT count(*) FROM ledger_entries WHERE tableoid = 'ledger_entries_default'::regclass
                    """)).isZero();
            assertThat(single(statement, """
                    SELECT count(*) FROM pg_inherits WHERE inhparent = 'ledger_entries'::regclass
                    """)).isGreaterThanOrEqualTo(16);
            assertSqlState(db, "SELECT count(*) FROM ledger_entries_default", PERMISSION_DENIED);
        }
    }

    private static long single(Statement statement, String sql) throws SQLException {
        try (var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void assertSqlState(Connection db, String sql, String sqlState) {
        assertThatThrownBy(() -> {
            try (var statement = db.createStatement()) {
                statement.execute(sql);
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as(sql).isEqualTo(sqlState));
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_APP, SenwalletPostgres.LEDGER_APP_PASSWORD);
    }
}
