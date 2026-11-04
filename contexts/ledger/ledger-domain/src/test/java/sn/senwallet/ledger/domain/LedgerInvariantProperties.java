package sn.senwallet.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.exception.UnbalancedTransactionException;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerTransactionRepository;
import sn.senwallet.ledger.domain.service.PostingService;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

/**
 * Exercise 1: whatever sequence of transactions is posted, the ledger stays
 * balanced and its counters agree with its entries.
 */
class LedgerInvariantProperties {

    private static final LedgerId LEDGER = LedgerId.newId();

    private static final int ACCOUNT_COUNT = 10;
    private static final int TRANSACTIONS_PER_TRY = 1_000;
    private static final int RECOMPUTE_EVERY = 100;
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Money ZERO = Money.zero(Currency.XOF);

    private final PostingService posting = new PostingService();

    /**
     * Accounts 0-5 are credit-normal (5 is an async revenue account), 6-9
     * debit-normal.
     */
    private static LedgerAccount account(int index) {
        var normal = index < 6 ? NormalBalance.CREDIT : NormalBalance.DEBIT;
        var mode = index == 5 ? BalanceMode.ASYNC : BalanceMode.SYNC;
        return LedgerAccount.open(AccountId.newId(), LEDGER, "account-" + index, normal, Currency.XOF, mode);
    }

    // 10 tries x 1 000 transactions = 10 000 random transactions.
    @Property(tries = 10)
    void randomTransactionsKeepTheLedgerBalanced(@ForAll("steps") List<Step> steps) {
        var accounts = new InMemoryLedgerAccountRepository();
        var transactions = new InMemoryLedgerTransactionRepository();
        var ids = IntStream.range(0, ACCOUNT_COUNT).mapToObj(i -> {
            var account = account(i);
            accounts.save(account);
            return account.id();
        }).toList();
        var pending = new ArrayList<TransactionId>();

        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var txn = LedgerTransaction.open(LEDGER, ExternalId.of("prop:" + i), toEntries(step, ids), step.status(), NOW);
            assertThat(txn.totalDebits()).isEqualTo(txn.totalCredits());

            var loaded = accounts.lockForUpdateSorted(txn.accountIds());
            var before = balancesOf(loaded);
            try {
                posting.apply(txn, loaded, step.guarded() ? noOverdraft(txn, loaded) : List.of());
                accounts.saveAll(loaded.values());
                transactions.save(txn);
                if (txn.status() == TransactionStatus.PENDING) {
                    pending.add(txn.id());
                }
                Statistics.label("outcome").collect("accepted");
            } catch (BalanceConditionViolatedException rejected) {
                assertThat(balancesOf(loaded)).isEqualTo(before);
                Statistics.label("outcome").collect("rejected");
            }

            if (step.resolution() != Resolution.NONE && !pending.isEmpty()) {
                var resolved = transactions.findById(pending.remove(step.pick() % pending.size())).orElseThrow();
                var touched = accounts.lockForUpdateSorted(resolved.accountIds());
                if (step.resolution() == Resolution.SETTLE) {
                    posting.settle(resolved, touched, NOW);
                } else {
                    posting.release(resolved, touched, NOW);
                }
                accounts.saveAll(touched.values());
                transactions.save(resolved);
            }

            assertTrialBalance(accounts.findAll());
            if ((i + 1) % RECOMPUTE_EVERY == 0) {
                assertCountersMatchEntries(accounts.findAll(), transactions.findAll());
            }
        }
        assertCountersMatchEntries(accounts.findAll(), transactions.findAll());
    }

    @Property(tries = 10_000)
    void openAcceptsExactlyTheBalancedEntrySets(@ForAll("entrySets") List<Entry> entries) {
        var debits = sum(entries, Direction.DEBIT);
        var credits = sum(entries, Direction.CREDIT);

        if (entries.size() >= 2 && debits.equals(credits)) {
            var txn = LedgerTransaction.open(LEDGER, ExternalId.of("balanced"), entries, TransactionStatus.POSTED, NOW);
            assertThat(txn.totalDebits()).isEqualTo(debits).isEqualTo(txn.totalCredits());
            Statistics.label("open").collect("accepted");
        } else {
            assertThatThrownBy(
                    () -> LedgerTransaction.open(LEDGER, ExternalId.of("unbalanced"), entries, TransactionStatus.POSTED, NOW))
                    .isInstanceOf(UnbalancedTransactionException.class);
            Statistics.label("open").collect("rejected");
        }
    }

    // --- invariants ---

    private static void assertTrialBalance(List<LedgerAccount> accounts) {
        var postedDebits = ZERO;
        var postedCredits = ZERO;
        var pendingDebits = ZERO;
        var pendingCredits = ZERO;
        for (var account : accounts) {
            var b = account.balances();
            for (var bucket : List.of(b.postedDebits(), b.postedCredits(), b.pendingDebits(), b.pendingCredits())) {
                assertThat(bucket.isNegative()).as("negative bucket on %s", account).isFalse();
            }
            postedDebits = postedDebits.plus(b.postedDebits());
            postedCredits = postedCredits.plus(b.postedCredits());
            pendingDebits = pendingDebits.plus(b.pendingDebits());
            pendingCredits = pendingCredits.plus(b.pendingCredits());
        }
        assertThat(postedDebits).as("sum of posted debits vs credits").isEqualTo(postedCredits);
        assertThat(pendingDebits).as("sum of pending debits vs credits").isEqualTo(pendingCredits);
    }

    /** Rebuilds the four buckets of every account from the stored transactions. */
    private static void assertCountersMatchEntries(List<LedgerAccount> accounts, List<LedgerTransaction> txns) {
        var expected = new HashMap<AccountId, Balances>();
        accounts.forEach(account -> expected.put(account.id(), Balances.zero(Currency.XOF)));
        for (var txn : txns) {
            if (txn.status() == TransactionStatus.ARCHIVED) {
                continue;
            }
            for (var entry : txn.entries()) {
                expected.computeIfPresent(entry.accountId(), (id, b) -> txn.status() == TransactionStatus.POSTED
                        ? b.withPosted(entry.direction(), entry.amount())
                        : b.withPending(entry.direction(), entry.amount()));
            }
        }
        for (var account : accounts) {
            var actual = account.balances();
            var rebuilt = expected.get(account.id());
            assertThat(actual.postedDebits()).as("posted debits of %s", account).isEqualTo(rebuilt.postedDebits());
            assertThat(actual.postedCredits()).as("posted credits of %s", account).isEqualTo(rebuilt.postedCredits());
            assertThat(actual.pendingDebits()).as("pending debits of %s", account).isEqualTo(rebuilt.pendingDebits());
            assertThat(actual.pendingCredits())
                    .as("pending credits of %s", account)
                    .isEqualTo(rebuilt.pendingCredits());
        }
    }

    // --- generation ---

    enum Resolution {
        NONE, SETTLE, RELEASE
    }

    record Leg(int account, long amount) {
    }

    /**
     * One random transaction: random legs on one side, the same total split
     * over 1-3 accounts on the other side; optionally guarded by
     * {@code available >= 0} on debited wallets, and optionally resolving an
     * earlier pending transaction.
     */
    record Step(
            List<Leg> legs,
            boolean legsAreDebits,
            List<Integer> otherSide,
            TransactionStatus status,
            boolean guarded,
            Resolution resolution,
            int pick) {
    }

    @Provide
    Arbitrary<List<Step>> steps() {
        return step().list().ofSize(TRANSACTIONS_PER_TRY);
    }

    @Provide
    Arbitrary<List<Entry>> entrySets() {
        var ids = IntStream.range(0, 4).mapToObj(i -> AccountId.newId()).toList();
        var randomEntry = Combinators.combine(
                Arbitraries.of(ids),
                Arbitraries.of(Direction.class),
                Arbitraries.longs().between(1, 1_000))
                .as((id, direction, amount) -> new Entry(id, direction, Amount.xof(amount)));
        var random = randomEntry.list().ofMinSize(0).ofMaxSize(6);
        var balanced = step().map(step -> toEntries(step, ids));
        return Arbitraries.oneOf(List.of(random, balanced));
    }

    private static Arbitrary<Step> step() {
        var accountIndex = Arbitraries.integers().between(0, ACCOUNT_COUNT - 1);
        var leg = Combinators.combine(accountIndex, Arbitraries.longs().between(1, 1_000_000)).as(Leg::new);
        return Combinators.combine(
                leg.list().ofMinSize(1).ofMaxSize(3),
                Arbitraries.of(true, false),
                accountIndex.list().ofMinSize(1).ofMaxSize(3),
                Arbitraries.of(TransactionStatus.PENDING, TransactionStatus.POSTED),
                Arbitraries.of(true, false),
                Arbitraries.of(Resolution.class),
                Arbitraries.integers().greaterOrEqual(0))
                .as(Step::new);
    }

    private static List<Entry> toEntries(Step step, List<AccountId> ids) {
        var side = step.legsAreDebits() ? Direction.DEBIT : Direction.CREDIT;
        var otherSide = step.legsAreDebits() ? Direction.CREDIT : Direction.DEBIT;
        var entries = new ArrayList<Entry>();
        long total = 0;
        for (var leg : step.legs()) {
            entries.add(new Entry(ids.get(leg.account() % ids.size()), side, Amount.xof(leg.amount())));
            total += leg.amount();
        }
        int parts = (int) Math.min(step.otherSide().size(), total);
        long share = total / parts;
        for (int i = 0; i < parts; i++) {
            long amount = i == 0 ? share + total % parts : share;
            var id = ids.get(step.otherSide().get(i) % ids.size());
            entries.add(new Entry(id, otherSide, Amount.xof(amount)));
        }
        return entries;
    }

    private static List<BalanceCondition> noOverdraft(LedgerTransaction txn, Map<AccountId, LedgerAccount> loaded) {
        var guarded = new LinkedHashSet<AccountId>();
        for (var entry : txn.entries()) {
            var account = loaded.get(entry.accountId());
            if (entry.isDebit()
                    && account.normalBalance() == NormalBalance.CREDIT
                    && account.balanceMode() == BalanceMode.SYNC) {
                guarded.add(account.id());
            }
        }
        return guarded.stream()
                .map(id -> BalanceCondition.of(id, BalanceType.AVAILABLE, Operator.GTE, ZERO))
                .toList();
    }

    private static Map<AccountId, Balances> balancesOf(Map<AccountId, LedgerAccount> accounts) {
        var snapshot = new HashMap<AccountId, Balances>();
        accounts.forEach((id, account) -> snapshot.put(id, account.balances()));
        return snapshot;
    }

    private static Money sum(List<Entry> entries, Direction direction) {
        return entries.stream()
                .filter(entry -> entry.direction() == direction)
                .map(entry -> entry.amount().value())
                .reduce(ZERO, Money::plus);
    }
}
