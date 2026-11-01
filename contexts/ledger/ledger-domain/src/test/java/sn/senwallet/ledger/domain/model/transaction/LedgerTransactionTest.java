package sn.senwallet.ledger.domain.model.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.ARCHIVED;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.PENDING;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.domain.event.TransactionArchived;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.ledger.domain.exception.UnbalancedTransactionException;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.money.Money;

class LedgerTransactionTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private final AccountId adama = AccountId.newId();
    private final AccountId binta = AccountId.newId();
    private final AccountId revenue = AccountId.newId();

    private List<Entry> p2pWithFee() {
        return List.of(
                Entry.debit(adama, Amount.xof(50_500)),
                Entry.credit(binta, Amount.xof(50_000)),
                Entry.credit(revenue, Amount.xof(500)));
    }

    @Test
    void opensABalancedTransaction() {
        var txn = LedgerTransaction.open(ExternalId.of("p2p:1"), p2pWithFee(), POSTED, T0);

        assertThat(txn.status()).isEqualTo(POSTED);
        assertThat(txn.totalDebits()).isEqualTo(Money.xof(50_500)).isEqualTo(txn.totalCredits());
        assertThat(txn.postedAt()).contains(T0);
        assertThat(List.copyOf(txn.accountIds()))
                .containsExactlyInAnyOrder(adama, binta, revenue)
                .isSorted();
        assertThat(txn.pullEvents()).singleElement().isInstanceOf(TransactionPosted.class);
        assertThat(txn.pullEvents()).isEmpty();
    }

    @Test
    void refusesFewerThanTwoEntries() {
        assertThatThrownBy(() -> LedgerTransaction.open(
                ExternalId.of("x"), List.of(Entry.debit(adama, Amount.xof(1))), POSTED, T0))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("two entries");
        assertThatThrownBy(() -> LedgerTransaction.open(ExternalId.of("x"), List.of(), POSTED, T0))
                .isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void refusesDebitsDifferentFromCredits() {
        var entries = List.of(Entry.debit(adama, Amount.xof(50_500)), Entry.credit(binta, Amount.xof(50_000)));

        assertThatThrownBy(() -> LedgerTransaction.open(ExternalId.of("x"), entries, POSTED, T0))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("50500 XOF")
                .hasMessageContaining("50000 XOF");
    }

    @Test
    void refusesToOpenAsArchived() {
        assertThatThrownBy(() -> LedgerTransaction.open(ExternalId.of("x"), p2pWithFee(), ARCHIVED, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingCanBePosted() {
        var txn = LedgerTransaction.open(ExternalId.of("cashout:1"), p2pWithFee(), PENDING, T0);
        assertThat(txn.postedAt()).isEmpty();

        txn.post(T1);

        assertThat(txn.status()).isEqualTo(POSTED);
        assertThat(txn.postedAt()).contains(T1);
        assertThat(txn.pullEvents())
                .hasExactlyElementsOfTypes(TransactionPending.class, TransactionPosted.class);
    }

    @Test
    void pendingCanBeArchived() {
        var txn = LedgerTransaction.open(ExternalId.of("cashout:1"), p2pWithFee(), PENDING, T0);

        txn.archive(T1);

        assertThat(txn.status()).isEqualTo(ARCHIVED);
        assertThat(txn.postedAt()).isEmpty();
        assertThat(txn.pullEvents())
                .hasExactlyElementsOfTypes(TransactionPending.class, TransactionArchived.class);
    }

    @Test
    void postedAndArchivedAreFinal() {
        var posted = LedgerTransaction.open(ExternalId.of("a"), p2pWithFee(), POSTED, T0);
        assertThatThrownBy(() -> posted.post(T1)).isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> posted.archive(T1)).isInstanceOf(IllegalTransitionException.class);

        var archived = LedgerTransaction.open(ExternalId.of("b"), p2pWithFee(), PENDING, T0);
        archived.archive(T1);
        assertThatThrownBy(() -> archived.post(T1))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("ARCHIVED -> POSTED");
    }

    @Test
    void entriesCannotBeModified() {
        var txn = LedgerTransaction.open(ExternalId.of("a"), p2pWithFee(), POSTED, T0);

        assertThatThrownBy(() -> txn.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
