package sn.senwallet.ledger.domain.model.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.account.NormalBalance.CREDIT;
import static sn.senwallet.ledger.domain.model.account.NormalBalance.DEBIT;

import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

class BalancesTest {

    private static Balances of(long postedDr, long postedCr, long pendingDr, long pendingCr) {
        return new Balances(Money.xof(postedDr), Money.xof(postedCr), Money.xof(pendingDr), Money.xof(pendingCr), 0);
    }

    @Test
    void readmeExampleSettledFundsPlusIncomingTopUp() {
        // Adama: 49 500 settled, a 20 000 Wave top-up still pending.
        var adama = of(50_500, 100_000, 0, 20_000);

        assertThat(adama.posted(CREDIT)).isEqualTo(Money.xof(49_500));
        assertThat(adama.pending(CREDIT)).isEqualTo(Money.xof(69_500));
        assertThat(adama.available(CREDIT)).isEqualTo(Money.xof(49_500));
    }

    @Test
    void creditNormalOutgoingPendingReducesAvailableOnly() {
        var wallet = of(0, 100_000, 30_000, 0);

        assertThat(wallet.posted(CREDIT)).isEqualTo(Money.xof(100_000));
        assertThat(wallet.pending(CREDIT)).isEqualTo(Money.xof(70_000));
        assertThat(wallet.available(CREDIT)).isEqualTo(Money.xof(70_000));
    }

    @Test
    void debitNormalInvertsDebitsAndCredits() {
        var asset = of(100_000, 0, 0, 30_000);

        assertThat(asset.posted(DEBIT)).isEqualTo(Money.xof(100_000));
        assertThat(asset.pending(DEBIT)).isEqualTo(Money.xof(70_000));
        assertThat(asset.available(DEBIT)).isEqualTo(Money.xof(70_000));

        var withIncoming = of(100_000, 0, 20_000, 0);
        assertThat(withIncoming.pending(DEBIT)).isEqualTo(Money.xof(120_000));
        assertThat(withIncoming.available(DEBIT)).isEqualTo(Money.xof(100_000));
    }

    @Test
    void balancesCanGoNegativeButBucketsCannot() {
        var overdrawn = of(10, 0, 0, 0);
        assertThat(overdrawn.posted(CREDIT)).isEqualTo(Money.xof(-10));

        assertThatThrownBy(() -> of(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void settlingMovesPendingToPostedAndReleasingDropsIt() {
        var amount = Amount.xof(30_000);
        var pending = Balances.zero(Currency.XOF).withPending(Direction.DEBIT, amount);

        assertThat(pending.settled(Direction.DEBIT, amount)).isEqualTo(of(30_000, 0, 0, 0));
        assertThat(pending.released(Direction.DEBIT, amount)).isEqualTo(of(0, 0, 0, 0));
    }

    @Test
    void releasingMoreThanPendingIsRefused() {
        var pending = Balances.zero(Currency.XOF).withPending(Direction.CREDIT, Amount.xof(100));

        assertThatThrownBy(() -> pending.released(Direction.CREDIT, Amount.xof(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.released(Direction.DEBIT, Amount.xof(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextVersionOnlyBumpsTheLockVersion() {
        var balances = of(1, 2, 3, 4);

        var next = balances.nextVersion();

        assertThat(next.lockVersion()).isEqualTo(1);
        assertThat(next.postedDebits()).isEqualTo(balances.postedDebits());
    }
}
