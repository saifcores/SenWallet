package sn.senwallet.ledger.application.service;

import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.exception.AccountNotFoundException;
import sn.senwallet.ledger.application.exception.LedgerNotFoundException;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.application.port.in.AccountBalances;
import sn.senwallet.ledger.application.port.in.BalanceQuery;
import sn.senwallet.ledger.application.port.in.LedgerLookupQuery;
import sn.senwallet.ledger.application.port.out.BalanceHistoryPort;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;

@Transactional(readOnly = true)
public class LedgerQueryService implements LedgerLookupQuery, BalanceQuery {

    private final LedgerRepository ledgers;
    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final BalanceHistoryPort history;

    public LedgerQueryService(
            LedgerRepository ledgers,
            LedgerAccountRepository accounts,
            LedgerTransactionRepository transactions,
            BalanceHistoryPort history) {
        this.ledgers = ledgers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.history = history;
    }

    @Override
    public Ledger ledger(LedgerId id) {
        return ledgers.findById(id).orElseThrow(() -> new LedgerNotFoundException(id));
    }

    @Override
    public LedgerAccount account(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Override
    public LedgerTransaction transaction(TransactionId id) {
        return transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Override
    public AccountBalances current(AccountId accountId) {
        return AccountBalances.of(account(accountId));
    }

    @Override
    public AccountBalances asOf(AccountId accountId, Instant asOf) {
        var account = account(accountId);
        return AccountBalances.of(accountId, account.normalBalance(), history.balancesAsOf(account, asOf));
    }
}
