package sn.senwallet.wallet.infrastructure.adapter.out.ledger;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.clients.ledger.LedgerClient;
import sn.senwallet.clients.ledger.LedgerDtos.Condition;
import sn.senwallet.clients.ledger.LedgerDtos.Entry;
import sn.senwallet.clients.ledger.LedgerDtos.OpenAccount;
import sn.senwallet.clients.ledger.LedgerDtos.PostTransaction;
import sn.senwallet.clients.ledger.LedgerRejectedException;
import sn.senwallet.clients.ledger.LedgerTransportException;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.domain.model.P2pPosting;

@Component
public class HttpLedgerGateway implements LedgerGateway {

    private static final long PLATFORM_LOCK = 7_482_004L;

    private final JdbcClient jdbc;
    private final LedgerClient ledger;

    public HttpLedgerGateway(JdbcClient jdbc, LedgerClient ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public PlatformAccounts ensurePlatform() {
        // The function returns void. Mapping that column to a long fails.
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", PLATFORM_LOCK)
                .query((rs, row) -> 1)
                .single();
        var existing = jdbc.sql("SELECT ledger_id, fee_account_id FROM platform_accounts")
                .query((rs, row) -> new PlatformAccounts(
                        new LedgerId(rs.getObject("ledger_id", UUID.class)),
                        new AccountId(rs.getObject("fee_account_id", UUID.class))))
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        var ledgerId = call(() -> ledger.createLedger("SenWallet", "live"));
        var feeAccountId = call(() -> ledger.openAccount(new OpenAccount(
                ledgerId, "Revenus - frais P2P", "credit", "XOF", "async")));
        jdbc.sql("""
                INSERT INTO platform_accounts (singleton, ledger_id, fee_account_id)
                VALUES (true, :ledgerId, :feeAccountId)
                """)
                .param("ledgerId", ledgerId)
                .param("feeAccountId", feeAccountId)
                .update();
        return new PlatformAccounts(new LedgerId(ledgerId), new AccountId(feeAccountId));
    }

    @Override
    public AccountId openWalletAccount(LedgerId ledgerId, String name) {
        var accountId = call(() -> ledger.openAccount(new OpenAccount(
                ledgerId.value(), name, "credit", "XOF", "sync")));
        return new AccountId(accountId);
    }

    @Override
    public PostResult post(P2pPosting posting) {
        var entries = posting.entries().stream()
                .map(line -> new Entry(line.accountId(), line.direction().name().toLowerCase(java.util.Locale.ROOT),
                        line.amount()))
                .toList();
        var conditions = posting.conditions().stream()
                .map(condition -> new Condition(condition.accountId(), condition.balance(), condition.operator(),
                        condition.amount()))
                .toList();
        var request = new PostTransaction(
                requirePlatform().ledgerId().value(), posting.externalId(), "posted", "XOF", "P2P", entries,
                conditions);
        try {
            return PostResult.posted(ledger.postTransaction(request));
        } catch (LedgerRejectedException rejected) {
            return PostResult.rejected(rejected.status(), rejected.code(), rejected.getMessage());
        } catch (LedgerTransportException failure) {
            throw new WalletProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }

    private PlatformAccounts requirePlatform() {
        return jdbc.sql("SELECT ledger_id, fee_account_id FROM platform_accounts")
                .query((rs, row) -> new PlatformAccounts(
                        new LedgerId(rs.getObject("ledger_id", UUID.class)),
                        new AccountId(rs.getObject("fee_account_id", UUID.class))))
                .optional()
                .orElseThrow(() -> new IllegalStateException("platform accounts are not initialised"));
    }

    @Override
    public LedgerTransaction transaction(UUID transactionId) {
        var transaction = call(() -> ledger.getTransaction(transactionId));
        var lines = transaction.entries().stream()
                .map(line -> new LedgerLine(line.accountId(), line.direction(), line.amount()))
                .toList();
        return new LedgerTransaction(transaction.id(), transaction.externalId(), lines);
    }

    @Override
    public LedgerGateway.Balances balances(AccountId accountId) {
        var balances = call(() -> ledger.getBalances(accountId.value()));
        return new LedgerGateway.Balances(balances.posted(), balances.pending(), balances.available());
    }

    private static <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (LedgerTransportException e) {
            throw new WalletProblem(503, "ledger_unavailable", "ledger is unavailable");
        }
    }
}
