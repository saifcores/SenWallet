package sn.senwallet.ledger.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sn.senwallet.ledger.application.exception.IdempotencyConflictException;
import sn.senwallet.ledger.application.port.in.AccountBalances;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.RequestHash;
import sn.senwallet.ledger.domain.repository.LedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.LedgerTransactionRepository;
import sn.senwallet.ledger.domain.service.PostingService;
import sn.senwallet.shared.id.AccountId;

/**
 * Steps 3 to 6 of the posting algorithm (section 4 of the architecture
 * dossier), shared by postings and reversals. Must run inside the caller's
 * database transaction: any exception rolls the header insert back too.
 */
final class TransactionWriter {

    private final LedgerTransactionRepository transactions;
    private final LedgerAccountRepository accounts;
    private final PostingService posting = new PostingService();

    TransactionWriter(LedgerTransactionRepository transactions, LedgerAccountRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    PostingResult write(LedgerTransaction txn, RequestHash hash, List<BalanceCondition> conditions) {
        if (!transactions.insertIfAbsent(txn, hash)) {
            return replay(txn, hash);
        }
        var locked = accounts.lockForUpdateSorted(txn.accountIds());
        posting.apply(txn, locked, conditions);
        transactions.insertEntries(txn, versions(locked));
        accounts.saveBalances(locked.values());
        // Events are published through the outbox from M3 on.
        txn.pullEvents();
        return new PostingResult.Created(txn, balances(locked));
    }

    private PostingResult replay(LedgerTransaction txn, RequestHash hash) {
        var stored = transactions.findByExternalId(txn.ledgerId(), txn.externalId())
                .orElseThrow(() -> new IllegalStateException("external id conflict without a stored transaction: "
                        + txn.externalId()));
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyConflictException(txn.externalId());
        }
        var existing = stored.transaction();
        var current = existing.accountIds().stream()
                .map(id -> accounts.findById(id).orElseThrow())
                .map(AccountBalances::of)
                .toList();
        return new PostingResult.Replayed(existing, current);
    }

    static Map<AccountId, Long> versions(Map<AccountId, LedgerAccount> locked) {
        var versions = new HashMap<AccountId, Long>();
        locked.forEach((id, account) -> versions.put(id, account.balances().lockVersion()));
        return versions;
    }

    static List<AccountBalances> balances(Map<AccountId, LedgerAccount> locked) {
        return locked.values().stream().map(AccountBalances::of).toList();
    }
}
