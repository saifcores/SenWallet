package sn.senwallet.ledger.domain.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import sn.senwallet.ledger.domain.exception.AccountNotPostableException;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.CurrencyMismatchException;

/**
 * Applies transactions to account counters. Every operation computes the new
 * balances first and only writes them to the accounts once all checks pass, so
 * a rejected operation leaves the accounts exactly as they were.
 */
public final class PostingService {

    /**
     * Applies a newly opened transaction: to the pending buckets if it is
     * {@code PENDING}, to the posted buckets otherwise, then evaluates the
     * conditions against the resulting balances.
     */
    public void apply(
            LedgerTransaction txn, Map<AccountId, LedgerAccount> accounts, List<BalanceCondition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (txn.status() == TransactionStatus.ARCHIVED) {
            throw new IllegalArgumentException("cannot apply an archived transaction: " + txn);
        }
        requirePostable(txn, accounts);
        for (var condition : conditions) {
            var account = accounts.get(condition.accountId());
            if (account == null || !txn.accountIds().contains(condition.accountId())) {
                throw new IllegalArgumentException("condition on an account outside the transaction: " + condition);
            }
            if (account.balanceMode() == BalanceMode.ASYNC) {
                throw new IllegalArgumentException("condition on an async account: " + condition);
            }
            if (condition.threshold().currency() != account.currency()) {
                throw new CurrencyMismatchException(account.currency(), condition.threshold().currency());
            }
        }

        var projected = project(txn, accounts, txn.status() == TransactionStatus.PENDING
                ? (balances, entry) -> balances.withPending(entry.direction(), entry.amount())
                : (balances, entry) -> balances.withPosted(entry.direction(), entry.amount()));

        for (var condition : conditions) {
            var balances = projected.get(condition.accountId());
            var normal = accounts.get(condition.accountId()).normalBalance();
            if (!condition.isSatisfiedBy(balances, normal)) {
                throw new BalanceConditionViolatedException(condition, condition.balanceOf(balances, normal));
            }
        }
        commit(projected, accounts);
    }

    /**
     * {@code PENDING -> POSTED}: moves the amounts from the pending to the posted
     * buckets.
     */
    public void settle(LedgerTransaction txn, Map<AccountId, LedgerAccount> accounts, Instant now) {
        requirePostable(txn, accounts);
        requirePending(txn, TransactionStatus.POSTED);
        var projected = project(txn, accounts,
                (balances, entry) -> balances.settled(entry.direction(), entry.amount()));
        txn.post(now);
        commit(projected, accounts);
    }

    /**
     * {@code PENDING -> ARCHIVED}: removes the amounts from the pending buckets.
     */
    public void release(LedgerTransaction txn, Map<AccountId, LedgerAccount> accounts, Instant now) {
        requirePostable(txn, accounts);
        requirePending(txn, TransactionStatus.ARCHIVED);
        var projected = project(txn, accounts,
                (balances, entry) -> balances.released(entry.direction(), entry.amount()));
        txn.archive(now);
        commit(projected, accounts);
    }

    private static void requirePostable(LedgerTransaction txn, Map<AccountId, LedgerAccount> accounts) {
        Objects.requireNonNull(txn, "txn");
        Objects.requireNonNull(accounts, "accounts");
        for (var id : txn.accountIds()) {
            var account = accounts.get(id);
            if (account == null) {
                throw new AccountNotPostableException(id, "not loaded");
            }
            if (!account.ledgerId().equals(txn.ledgerId())) {
                throw new AccountNotPostableException(id, "belongs to ledger " + account.ledgerId());
            }
            if (!account.isActive()) {
                throw new AccountNotPostableException(id, "status " + account.status());
            }
            if (account.currency() != txn.currency()) {
                throw new CurrencyMismatchException(account.currency(), txn.currency());
            }
        }
    }

    private static void requirePending(LedgerTransaction txn, TransactionStatus target) {
        if (txn.status() != TransactionStatus.PENDING) {
            throw new IllegalTransitionException(txn.status(), target);
        }
    }

    private static Map<AccountId, Balances> project(
            LedgerTransaction txn,
            Map<AccountId, LedgerAccount> accounts,
            BiFunction<Balances, Entry, Balances> step) {
        var projected = new HashMap<AccountId, Balances>();
        for (var entry : txn.entries()) {
            var current = projected.getOrDefault(entry.accountId(), accounts.get(entry.accountId()).balances());
            projected.put(entry.accountId(), step.apply(current, entry));
        }
        return projected;
    }

    private static void commit(Map<AccountId, Balances> projected, Map<AccountId, LedgerAccount> accounts) {
        projected.forEach((id, balances) -> accounts.get(id).update(balances));
    }
}
