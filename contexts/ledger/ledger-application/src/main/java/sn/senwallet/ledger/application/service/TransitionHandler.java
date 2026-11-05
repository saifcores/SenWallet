package sn.senwallet.ledger.application.service;

import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.ledger.application.command.TransitionCommand;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.application.port.in.AccountBalances;
import sn.senwallet.ledger.application.port.in.TransitionResult;
import sn.senwallet.ledger.application.port.in.TransitionTransactionUseCase;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.application.port.out.OutboxPort;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.ledger.domain.service.PostingService;

/**
 * {@code PENDING -> POSTED | ARCHIVED}. Asking for the status a transaction is
 * already in succeeds without writing, so callers can retry safely.
 */
@Transactional
public class TransitionHandler implements TransitionTransactionUseCase {

    private final LedgerTransactionRepository transactions;
    private final LedgerAccountRepository accounts;
    private final OutboxPort outbox;
    private final ClockPort clock;
    private final PostingService posting = new PostingService();

    public TransitionHandler(
            LedgerTransactionRepository transactions,
            LedgerAccountRepository accounts,
            OutboxPort outbox,
            ClockPort clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public TransitionResult transition(TransitionCommand cmd) {
        var txn = transactions.findByIdForUpdate(cmd.transactionId())
                .orElseThrow(() -> new TransactionNotFoundException(cmd.transactionId()));
        if (txn.status() == cmd.target()) {
            var current = txn.accountIds().stream()
                    .map(id -> accounts.findById(id).orElseThrow())
                    .map(AccountBalances::of)
                    .toList();
            return new TransitionResult.Unchanged(txn, current);
        }
        if (txn.status() != TransactionStatus.PENDING) {
            throw new IllegalTransitionException(txn.status(), cmd.target());
        }
        var locked = accounts.lockForUpdateSorted(txn.accountIds());
        if (cmd.target() == TransactionStatus.POSTED) {
            posting.settle(txn, locked, clock.now());
        } else {
            posting.release(txn, locked, clock.now());
        }
        transactions.updateStatus(txn);
        accounts.saveBalances(locked.values());
        outbox.append(txn, txn.pullEvents());
        return new TransitionResult.Transitioned(txn, TransactionWriter.balances(locked));
    }
}
