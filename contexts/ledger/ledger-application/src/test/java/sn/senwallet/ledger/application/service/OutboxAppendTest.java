package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.ARCHIVED;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.PENDING;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.application.command.TransitionCommand;
import sn.senwallet.ledger.domain.event.TransactionArchived;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;

class OutboxAppendTest {

    private final LedgerFixture f = new LedgerFixture();

    @Test
    void postingAppendsOneEventAndReplayAppendsNone() {
        f.post.post(f.fund("pispi-in:1", f.adama, 100_000));
        assertThat(f.outbox.events()).singleElement().isInstanceOf(TransactionPosted.class);

        f.post.post(f.fund("pispi-in:1", f.adama, 100_000));

        assertThat(f.outbox.events()).hasSize(1);
    }

    @Test
    void transitionAppendsOneEventAndARetryAppendsNone() {
        f.post.post(f.fund("pispi-in:1", f.adama, 50_000));
        var hold = f.post.post(f.transfer("cashout:1", f.adama, f.treasury, 10_000, PENDING)).transaction().id();
        f.outbox.clear();

        f.transition.transition(new TransitionCommand(hold, POSTED));
        assertThat(f.outbox.events()).singleElement().isInstanceOf(TransactionPosted.class);

        f.transition.transition(new TransitionCommand(hold, POSTED));
        assertThat(f.outbox.events()).hasSize(1);
    }

    @Test
    void archivingAppendsOneEvent() {
        f.post.post(f.fund("pispi-in:1", f.adama, 50_000));
        var hold = f.post.post(f.transfer("cashout:1", f.adama, f.treasury, 10_000, PENDING)).transaction().id();
        f.outbox.clear();

        f.transition.transition(new TransitionCommand(hold, ARCHIVED));

        assertThat(f.outbox.events()).singleElement().isInstanceOf(TransactionArchived.class);
    }

    @Test
    void aPendingPostingAppendsAPendingEvent() {
        f.post.post(f.fund("pispi-in:1", f.adama, 50_000));
        f.outbox.clear();

        f.post.post(f.transfer("cashout:1", f.adama, f.treasury, 10_000, PENDING));

        assertThat(f.outbox.events()).singleElement().isInstanceOf(TransactionPending.class);
    }

    @Test
    void reversalAppendsOneEventAndAReplayAppendsNone() {
        var funding = f.post.post(f.fund("pispi-in:1", f.adama, 10_000)).transaction().id();
        f.outbox.clear();
        var command = new ReverseTransactionCommand(funding, Optional.empty(), List.of());

        f.reverse.reverse(command);
        assertThat(f.outbox.events()).singleElement().isInstanceOf(TransactionPosted.class);

        f.reverse.reverse(command);
        assertThat(f.outbox.events()).hasSize(1);
    }

    @Test
    void aRejectedPostingAppendsNothing() {
        assertThatThrownBy(() -> f.post.post(f.transfer("p2p:1", f.adama, f.binta, 1, POSTED)))
                .isInstanceOf(BalanceConditionViolatedException.class);
        assertThat(f.outbox.events()).isEmpty();
    }
}
