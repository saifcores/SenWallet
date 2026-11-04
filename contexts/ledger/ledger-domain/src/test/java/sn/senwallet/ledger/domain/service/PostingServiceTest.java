package sn.senwallet.ledger.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.condition.BalanceType.AVAILABLE;
import static sn.senwallet.ledger.domain.model.condition.Operator.GTE;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.PENDING;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.domain.exception.AccountNotPostableException;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.model.account.AccountStatus;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

class PostingServiceTest {

    private static final LedgerId LEDGER = LedgerId.newId();

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final PostingService posting = new PostingService();
    private final LedgerAccount treasury = LedgerAccount.open(AccountId.newId(), LEDGER, "treasury", NormalBalance.DEBIT, Currency.XOF, BalanceMode.SYNC);
    private final LedgerAccount wallet = LedgerAccount.open(AccountId.newId(), LEDGER, "wallet", NormalBalance.CREDIT, Currency.XOF, BalanceMode.SYNC);
    private final LedgerAccount revenue = LedgerAccount.open(AccountId.newId(), LEDGER, "revenue", NormalBalance.CREDIT, Currency.XOF, BalanceMode.ASYNC);

    private Map<AccountId, LedgerAccount> accounts() {
        var map = new HashMap<AccountId, LedgerAccount>();
        for (var account : List.of(treasury, wallet, revenue)) {
            map.put(account.id(), account);
        }
        return map;
    }

    private static LedgerTransaction transfer(
            LedgerAccount from, LedgerAccount to, long amount, TransactionStatus status) {
        return LedgerTransaction.open(LEDGER, ExternalId.of("t:" + AccountId.newId()),
                List.of(Entry.debit(from.id(), Amount.xof(amount)), Entry.credit(to.id(), Amount.xof(amount))),
                status,
                NOW);
    }

    private static BalanceCondition notOverdrawn(LedgerAccount account) {
        return BalanceCondition.of(account.id(), AVAILABLE, GTE, Money.zero(Currency.XOF));
    }

    private void fundWallet(long amount) {
        posting.apply(transfer(treasury, wallet, amount, POSTED), accounts(), List.of());
    }

    @Test
    void postedTransactionHitsPostedBucketsAndBumpsVersions() {
        fundWallet(100_000);

        assertThat(wallet.posted()).isEqualTo(Money.xof(100_000));
        assertThat(treasury.posted()).isEqualTo(Money.xof(100_000));
        assertThat(wallet.balances().lockVersion()).isEqualTo(1);
        assertThat(revenue.balances().lockVersion()).isZero();
    }

    @Test
    void pendingTransactionHitsPendingBuckets() {
        fundWallet(100_000);

        posting.apply(transfer(wallet, treasury, 30_000, PENDING), accounts(), List.of(notOverdrawn(wallet)));

        assertThat(wallet.balances().pendingDebits()).isEqualTo(Money.xof(30_000));
        assertThat(wallet.posted()).isEqualTo(Money.xof(100_000));
        assertThat(wallet.available()).isEqualTo(Money.xof(70_000));
    }

    @Test
    void violatedConditionChangesNothing() {
        fundWallet(10_000);
        var before = Map.of(treasury.id(), treasury.balances(), wallet.id(), wallet.balances());

        assertThatThrownBy(() -> posting.apply(
                transfer(wallet, treasury, 10_001, POSTED), accounts(), List.of(notOverdrawn(wallet))))
                .isInstanceOf(BalanceConditionViolatedException.class)
                .hasMessageContaining("available >= 0 XOF")
                .hasMessageContaining("-1 XOF")
                .satisfies(e -> assertThat(((BalanceConditionViolatedException) e).condition().accountId())
                        .isEqualTo(wallet.id()));

        assertThat(treasury.balances()).isEqualTo(before.get(treasury.id()));
        assertThat(wallet.balances()).isEqualTo(before.get(wallet.id()));
    }

    @Test
    void conditionSeesTheWholeTransactionNotEachEntry() {
        fundWallet(1_000);
        var txn = LedgerTransaction.open(LEDGER, ExternalId.of("netting"),
                List.of(
                        Entry.debit(wallet.id(), Amount.xof(1_500)),
                        Entry.credit(wallet.id(), Amount.xof(1_000)),
                        Entry.credit(treasury.id(), Amount.xof(500))),
                POSTED,
                NOW);

        posting.apply(txn, accounts(), List.of(notOverdrawn(wallet)));

        assertThat(wallet.posted()).isEqualTo(Money.xof(500));
    }

    @Test
    void settleMovesPendingToPosted() {
        fundWallet(100_000);
        var cashOut = transfer(wallet, treasury, 30_000, PENDING);
        posting.apply(cashOut, accounts(), List.of(notOverdrawn(wallet)));

        posting.settle(cashOut, accounts(), NOW);

        assertThat(cashOut.status()).isEqualTo(POSTED);
        assertThat(wallet.balances().pendingDebits()).isEqualTo(Money.zero(Currency.XOF));
        assertThat(wallet.posted()).isEqualTo(Money.xof(70_000));
        assertThat(wallet.available()).isEqualTo(Money.xof(70_000));
    }

    @Test
    void releaseRemovesPendingAndRestoresAvailable() {
        fundWallet(100_000);
        var cashOut = transfer(wallet, treasury, 30_000, PENDING);
        posting.apply(cashOut, accounts(), List.of(notOverdrawn(wallet)));

        posting.release(cashOut, accounts(), NOW);

        assertThat(cashOut.status()).isEqualTo(TransactionStatus.ARCHIVED);
        assertThat(wallet.posted()).isEqualTo(Money.xof(100_000));
        assertThat(wallet.available()).isEqualTo(Money.xof(100_000));
        assertThat(treasury.balances().pendingCredits()).isEqualTo(Money.zero(Currency.XOF));
    }

    @Test
    void settlingAPostedTransactionIsRefusedWithoutTouchingAccounts() {
        var funding = transfer(treasury, wallet, 100, POSTED);
        posting.apply(funding, accounts(), List.of());
        var before = wallet.balances();

        assertThatThrownBy(() -> posting.settle(funding, accounts(), NOW))
                .isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> posting.release(funding, accounts(), NOW))
                .isInstanceOf(IllegalTransitionException.class);
        assertThat(wallet.balances()).isEqualTo(before);
    }

    @Test
    void refusesConditionsOnAsyncAccounts() {
        var fee = transfer(treasury, revenue, 500, POSTED);

        assertThatThrownBy(() -> posting.apply(fee, accounts(), List.of(notOverdrawn(revenue))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("async");
        assertThat(revenue.balances().lockVersion()).isZero();
    }

    @Test
    void refusesConditionsOnAccountsOutsideTheTransaction() {
        var fee = transfer(treasury, revenue, 500, POSTED);

        assertThatThrownBy(() -> posting.apply(fee, accounts(), List.of(notOverdrawn(wallet))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void refusesMissingAndInactiveAccounts() {
        var txn = transfer(treasury, wallet, 100, POSTED);
        var missing = new HashMap<>(accounts());
        missing.remove(wallet.id());

        assertThatThrownBy(() -> posting.apply(txn, missing, List.of()))
                .isInstanceOf(AccountNotPostableException.class)
                .hasMessageContaining("not loaded");

        var inactive = LedgerAccount.restore(wallet.id(), LEDGER, "wallet", NormalBalance.CREDIT, Currency.XOF,
                BalanceMode.SYNC, AccountStatus.INACTIVE, Map.of(), Balances.zero(Currency.XOF));
        var withInactive = new HashMap<>(accounts());
        withInactive.put(wallet.id(), inactive);

        assertThatThrownBy(() -> posting.apply(txn, withInactive, List.of()))
                .isInstanceOf(AccountNotPostableException.class)
                .hasMessageContaining("INACTIVE");
        assertThat(treasury.balances().lockVersion()).isZero();
    }

    @Test
    void refusesAccountsOfAnotherLedger() {
        var foreign = LedgerAccount.open(
                AccountId.newId(), LedgerId.newId(), "foreign", NormalBalance.CREDIT, Currency.XOF, BalanceMode.SYNC);
        var txn = transfer(treasury, foreign, 100, POSTED);
        var withForeign = new HashMap<>(accounts());
        withForeign.put(foreign.id(), foreign);

        assertThatThrownBy(() -> posting.apply(txn, withForeign, List.of()))
                .isInstanceOf(AccountNotPostableException.class)
                .hasMessageContaining("ledger");
        assertThat(foreign.balances().lockVersion()).isZero();
    }

    @Test
    void staleBalancesAreRefusedByTheAccount() {
        var stale = wallet.balances();
        fundWallet(100);

        assertThatThrownBy(() -> wallet.update(stale)).isInstanceOf(IllegalStateException.class);
    }
}
