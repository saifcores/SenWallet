package sn.senwallet.payments.infrastructure.adapter.out.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.clients.ledger.LedgerClient;
import sn.senwallet.clients.ledger.LedgerDtos.Entry;
import sn.senwallet.clients.ledger.LedgerDtos.OpenAccount;
import sn.senwallet.clients.ledger.LedgerDtos.PostTransaction;
import sn.senwallet.clients.ledger.LedgerRejectedException;
import sn.senwallet.clients.ledger.LedgerTransportException;
import sn.senwallet.payments.application.exception.HoldRejected;
import sn.senwallet.payments.application.exception.PaymentProblem;
import sn.senwallet.payments.application.port.out.LedgerPort;

@Component
public class HttpLedgerGateway implements LedgerPort {

    private static final long PLATFORM_LOCK = 7_482_007L;

    private final JdbcClient jdbc;
    private final LedgerClient ledger;

    public HttpLedgerGateway(JdbcClient jdbc, LedgerClient ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public RailAccounts ensureRails(UUID ledgerId, UUID feeAccountId) {
        lock();
        var existing = currentRails();
        if (existing.isPresent()) {
            return existing.get();
        }
        var waveFloat = open(ledgerId, "Float Wave", "debit");
        var orangeFloat = open(ledgerId, "Float Orange Money", "debit");
        var waveReceivable = open(ledgerId, "Créance Wave", "debit");
        var orangeReceivable = open(ledgerId, "Créance Orange Money", "debit");
        var compensation = open(ledgerId, "Compensation PI-SPI", "debit");
        var suspense = open(ledgerId, "Compte d'attente PI-SPI", "credit");
        jdbc.sql("""
                INSERT INTO platform_accounts (
                    singleton, ledger_id, fee_account_id, wave_float_id, orange_float_id,
                    wave_receivable_id, orange_receivable_id, pispi_compensation_id, suspense_account_id)
                VALUES (true, :ledgerId, :fee, :waveFloat, :orangeFloat, :waveReceivable,
                        :orangeReceivable, :compensation, :suspense)
                """)
                .param("ledgerId", ledgerId)
                .param("fee", feeAccountId)
                .param("waveFloat", waveFloat)
                .param("orangeFloat", orangeFloat)
                .param("waveReceivable", waveReceivable)
                .param("orangeReceivable", orangeReceivable)
                .param("compensation", compensation)
                .param("suspense", suspense)
                .update();
        return new RailAccounts(ledgerId, feeAccountId, waveFloat, orangeFloat, waveReceivable, orangeReceivable,
                compensation, suspense);
    }

    @Override
    public Optional<RailAccounts> currentRails() {
        return jdbc.sql("""
                SELECT ledger_id, fee_account_id, wave_float_id, orange_float_id,
                       wave_receivable_id, orange_receivable_id, pispi_compensation_id, suspense_account_id
                FROM platform_accounts
                """)
                .query((rs, row) -> new RailAccounts(
                        rs.getObject("ledger_id", UUID.class),
                        rs.getObject("fee_account_id", UUID.class),
                        rs.getObject("wave_float_id", UUID.class),
                        rs.getObject("orange_float_id", UUID.class),
                        rs.getObject("wave_receivable_id", UUID.class),
                        rs.getObject("orange_receivable_id", UUID.class),
                        rs.getObject("pispi_compensation_id", UUID.class),
                        rs.getObject("suspense_account_id", UUID.class)))
                .optional();
    }

    @Override
    public UUID place(Hold hold) {
        return post(hold, "pending");
    }

    @Override
    public UUID postNow(Hold hold) {
        return post(hold, "posted");
    }

    @Override
    public void post(UUID transactionId) {
        transition(transactionId, "posted");
    }

    @Override
    public void archive(UUID transactionId) {
        transition(transactionId, "archived");
    }

    @Override
    public String status(UUID transactionId) {
        try {
            return ledger.getTransaction(transactionId).status();
        } catch (LedgerTransportException failure) {
            throw new PaymentProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }

    private UUID post(Hold hold, String status) {
        var entries = hold.lines().stream()
                .map(line -> new Entry(line.accountId(), line.direction(), line.amount()))
                .toList();
        var conditions = hold.conditions().stream()
                .map(condition -> new sn.senwallet.clients.ledger.LedgerDtos.Condition(
                        condition.accountId(), condition.balance(), condition.operator(), condition.amount()))
                .toList();
        try {
            return ledger.postTransaction(new PostTransaction(
                    hold.ledgerId(), hold.externalId(), status, "XOF", hold.description(), entries, conditions));
        } catch (LedgerRejectedException rejected) {
            throw new HoldRejected(rejected.status(), rejected.code(), rejected.getMessage());
        } catch (LedgerTransportException failure) {
            throw new PaymentProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }

    private void transition(UUID transactionId, String status) {
        try {
            ledger.transition(transactionId, status);
        } catch (LedgerRejectedException rejected) {
            throw new HoldRejected(rejected.status(), rejected.code(), rejected.getMessage());
        } catch (LedgerTransportException failure) {
            throw new PaymentProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }

    private UUID open(UUID ledgerId, String name, String normalBalance) {
        try {
            return ledger.openAccount(new OpenAccount(ledgerId, name, normalBalance, "XOF", "sync"));
        } catch (LedgerTransportException failure) {
            throw new PaymentProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }

    private void lock() {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", PLATFORM_LOCK)
                .query((rs, row) -> 1)
                .single();
    }
}
