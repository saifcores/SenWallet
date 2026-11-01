package sn.senwallet.ledger.domain.model.transaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import sn.senwallet.ledger.domain.event.LedgerTransactionEvent;
import sn.senwallet.ledger.domain.event.TransactionArchived;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.exception.TransactionNotReversibleException;
import sn.senwallet.ledger.domain.exception.UnbalancedTransactionException;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.CurrencyMismatchException;
import sn.senwallet.shared.money.Money;

/**
 * A balanced business event: at least two entries, one currency, debits equal
 * to credits. Entries never change; only the status moves, and only out of
 * {@code PENDING}. A posted transaction is corrected by a reversal, never
 * edited.
 */
public final class LedgerTransaction {

    private final TransactionId id;
    private final LedgerId ledgerId;
    private final ExternalId externalId;
    private final List<Entry> entries;
    private final String description;
    private final Map<String, String> metadata;
    private final TransactionId reversesId;
    private final Instant effectiveAt;
    private TransactionStatus status;
    private Instant postedAt;
    private Instant archivedAt;
    private final List<LedgerTransactionEvent> events = new ArrayList<>();

    private LedgerTransaction(
            TransactionId id,
            LedgerId ledgerId,
            ExternalId externalId,
            List<Entry> entries,
            String description,
            Map<String, String> metadata,
            TransactionId reversesId,
            TransactionStatus status,
            Instant effectiveAt,
            Instant postedAt,
            Instant archivedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId");
        this.externalId = Objects.requireNonNull(externalId, "externalId");
        this.entries = requireBalanced(entries);
        this.description = description;
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        this.reversesId = reversesId;
        this.status = Objects.requireNonNull(status, "status");
        this.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
        this.postedAt = postedAt;
        this.archivedAt = archivedAt;
    }

    public static LedgerTransaction open(
            LedgerId ledgerId, ExternalId externalId, List<Entry> entries, TransactionStatus status, Instant now) {
        return open(ledgerId, externalId, entries, status, null, Map.of(), now);
    }

    /**
     * Opens a transaction directly as {@code POSTED}, or as {@code PENDING} to
     * settle or archive later.
     */
    public static LedgerTransaction open(
            LedgerId ledgerId,
            ExternalId externalId,
            List<Entry> entries,
            TransactionStatus status,
            String description,
            Map<String, String> metadata,
            Instant now) {
        return opened(ledgerId, externalId, entries, status, description, metadata, null, now);
    }

    /** Rebuilds a transaction from storage; no event is recorded. */
    public static LedgerTransaction restore(
            TransactionId id,
            LedgerId ledgerId,
            ExternalId externalId,
            List<Entry> entries,
            String description,
            Map<String, String> metadata,
            TransactionId reversesId,
            TransactionStatus status,
            Instant effectiveAt,
            Instant postedAt,
            Instant archivedAt) {
        return new LedgerTransaction(id, ledgerId, externalId, entries, description, metadata, reversesId, status,
                effectiveAt, postedAt, archivedAt);
    }

    /**
     * A new {@code POSTED} transaction that undoes this one: same accounts and
     * amounts, every direction flipped.
     */
    public LedgerTransaction reversal(ExternalId reversalExternalId, Instant now) {
        if (status != TransactionStatus.POSTED) {
            throw new TransactionNotReversibleException(id, status);
        }
        var flipped = entries.stream()
                .map(e -> new Entry(e.accountId(), e.direction().opposite(), e.amount()))
                .toList();
        return opened(ledgerId, reversalExternalId, flipped, TransactionStatus.POSTED,
                "reversal of " + externalId, metadata, id, now);
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
        archivedAt = now;
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

    public LedgerId ledgerId() {
        return ledgerId;
    }

    public ExternalId externalId() {
        return externalId;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public Optional<TransactionId> reversesId() {
        return Optional.ofNullable(reversesId);
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

    public Optional<Instant> archivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    private static LedgerTransaction opened(
            LedgerId ledgerId,
            ExternalId externalId,
            List<Entry> entries,
            TransactionStatus status,
            String description,
            Map<String, String> metadata,
            TransactionId reversesId,
            Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(now, "now");
        if (status == TransactionStatus.ARCHIVED) {
            throw new IllegalArgumentException("a transaction opens as PENDING or POSTED, not ARCHIVED");
        }
        var txn = new LedgerTransaction(TransactionId.newId(), ledgerId, externalId, entries, description, metadata,
                reversesId, status, now, status == TransactionStatus.POSTED ? now : null, null);
        txn.events.add(status == TransactionStatus.POSTED
                ? TransactionPosted.of(txn.id, externalId, now)
                : TransactionPending.of(txn.id, externalId, now));
        return txn;
    }

    private static List<Entry> requireBalanced(List<Entry> entries) {
        var copy = List.copyOf(Objects.requireNonNull(entries, "entries"));
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
        return copy;
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
