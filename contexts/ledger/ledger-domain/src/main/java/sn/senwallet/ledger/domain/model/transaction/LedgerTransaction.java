package sn.senwallet.ledger.domain.model.transaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.event.TransactionArchived;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.exception.UnbalancedTransactionException;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.CurrencyMismatchException;
import sn.senwallet.shared.money.Money;

/**
 * A balanced business event: at least two entries, one currency, debits equal
 * to credits. Entries never change; only the status moves, and only out of
 * {@code PENDING}.
 */
public final class LedgerTransaction {

    private final TransactionId id;
    private final ExternalId externalId;
    private final List<Entry> entries;
    private final Instant effectiveAt;
    private TransactionStatus status;
    private Instant postedAt;
    private final List<LedgerTransactionEvent> events = new ArrayList<>();

    private LedgerTransaction(
            TransactionId id, ExternalId externalId, List<Entry> entries, TransactionStatus status, Instant now) {
        this.id = id;
        this.externalId = externalId;
        this.entries = entries;
        this.status = status;
        this.effectiveAt = now;
        this.postedAt = status == TransactionStatus.POSTED ? now : null;
    }

    /**
     * Opens a transaction directly as {@code POSTED}, or as {@code PENDING} to
     * settle or archive later.
     */
    public static LedgerTransaction open(
            ExternalId externalId, List<Entry> entries, TransactionStatus status, Instant now) {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(now, "now");
        if (status == TransactionStatus.ARCHIVED) {
            throw new IllegalArgumentException("a transaction opens as PENDING or POSTED, not ARCHIVED");
        }
        var copy = List.copyOf(entries);
        if (copy.size() < 2) {
            throw new UnbalancedTransactionException("at least two entries required, got " + copy.size());
        }
        var currency = copy.getFirst().amount().currency();
        for (var entry : copy) {
            if (entry.amount().currency() != currency) {
                throw new CurrencyMismatchException(currency, entry.amount().currency());
            }
        }
        var debits = sum(copy, Direction.DEBIT, currency);
        var credits = sum(copy, Direction.CREDIT, currency);
        if (!debits.equals(credits)) {
            throw new UnbalancedTransactionException(debits, credits);
        }

        var txn = new LedgerTransaction(TransactionId.newId(), externalId, copy, status, now);
        txn.events.add(status == TransactionStatus.POSTED
                ? TransactionPosted.of(txn.id, externalId, now)
                : TransactionPending.of(txn.id, externalId, now));
        return txn;
    }

    public void post(Instant now) {
        Objects.requireNonNull(now, "now");
        requirePending(TransactionStatus.POSTED);
        status = TransactionStatus.POSTED;
        postedAt = now;
        events.add(TransactionPosted.of(id, externalId, now));
    }

    public void archive(Instant now) {
        Objects.requireNonNull(now, "now");
        requirePending(TransactionStatus.ARCHIVED);
        status = TransactionStatus.ARCHIVED;
        events.add(TransactionArchived.of(id, externalId, now));
    }

    /** Returns the events recorded since the last call and forgets them. */
    public List<LedgerTransactionEvent> pullEvents() {
        var pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Accounts touched by this transaction, in lock order. */
    public SortedSet<AccountId> accountIds() {
        var ids = new TreeSet<AccountId>();
        entries.forEach(entry -> ids.add(entry.accountId()));
        return Collections.unmodifiableSortedSet(ids);
    }

    public Currency currency() {
        return entries.getFirst().amount().currency();
    }

    public Money totalDebits() {
        return sum(entries, Direction.DEBIT, currency());
    }

    public Money totalCredits() {
        return sum(entries, Direction.CREDIT, currency());
    }

    public TransactionId id() {
        return id;
    }

    public ExternalId externalId() {
        return externalId;
    }

    public List<Entry> entries() {
        return entries;
    }

    public TransactionStatus status() {
        return status;
    }

    public Instant effectiveAt() {
        return effectiveAt;
    }

    public Optional<Instant> postedAt() {
        return Optional.ofNullable(postedAt);
    }

    private void requirePending(TransactionStatus target) {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalTransitionException(status, target);
        }
    }

    private static Money sum(List<Entry> entries, Direction direction, Currency currency) {
        var total = Money.zero(currency);
        for (var entry : entries) {
            if (entry.direction() == direction) {
                total = total.plus(entry.amount().value());
            }
        }
        return total;
    }

    @Override
    public String toString() {
        return "LedgerTransaction[" + externalId + " " + status + "]";
    }
}
