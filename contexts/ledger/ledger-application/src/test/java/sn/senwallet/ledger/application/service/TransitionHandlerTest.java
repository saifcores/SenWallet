package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.ARCHIVED;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.PENDING;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.command.TransitionCommand;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.application.port.in.TransitionResult;
import sn.senwallet.ledger.domain.exception.IllegalTransitionException;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Money;

class TransitionHandlerTest {

    private final LedgerFixture f = new LedgerFixture();
    private TransactionId cashOut;

    @BeforeEach
    void holdFunds() {
        f.post.post(f.fund("pispi-in:1", f.adama, 100_000));
        cashOut = f.post.post(f.transfer("cashout:1", f.adama, f.treasury, 30_000, PENDING)).transaction().id();
        assertThat(f.available(f.adama)).isEqualTo(Money.xof(70_000));
    }

    @Test
    void postingSettlesTheHold() {
        var result = f.transition.transition(new TransitionCommand(cashOut, POSTED));

        assertThat(result).isInstanceOf(TransitionResult.Transitioned.class);
        assertThat(result.transaction().status()).isEqualTo(POSTED);
        assertThat(f.posted(f.adama)).isEqualTo(Money.xof(70_000));
        assertThat(f.transactions.findById(cashOut).orElseThrow().status()).isEqualTo(POSTED);
    }

    @Test
    void archivingReleasesTheHold() {
        f.transition.transition(new TransitionCommand(cashOut, ARCHIVED));

        assertThat(f.available(f.adama)).isEqualTo(Money.xof(100_000));
        assertThat(f.transactions.findById(cashOut).orElseThrow().archivedAt()).contains(LedgerFixture.NOW);
    }

    @Test
    void repeatingATransitionChangesNothing() {
        f.transition.transition(new TransitionCommand(cashOut, POSTED));

        var retry = f.transition.transition(new TransitionCommand(cashOut, POSTED));

        assertThat(retry).isInstanceOf(TransitionResult.Unchanged.class);
        assertThat(f.posted(f.adama)).isEqualTo(Money.xof(70_000));
    }

    @Test
    void finalStatusesCannotChange() {
        f.transition.transition(new TransitionCommand(cashOut, ARCHIVED));

        assertThatThrownBy(() -> f.transition.transition(new TransitionCommand(cashOut, POSTED)))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("ARCHIVED -> POSTED");
        assertThat(f.available(f.adama)).isEqualTo(Money.xof(100_000));
    }

    @Test
    void unknownTransactionIsNotFound() {
        assertThatThrownBy(() -> f.transition.transition(new TransitionCommand(TransactionId.newId(), POSTED)))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void pendingIsNotATarget() {
        assertThatThrownBy(() -> new TransitionCommand(cashOut, PENDING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
