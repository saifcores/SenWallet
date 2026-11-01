package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.PENDING;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.domain.exception.TransactionAlreadyReversedException;
import sn.senwallet.ledger.domain.exception.TransactionNotReversibleException;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Money;

class ReverseHandlerTest {

    private final LedgerFixture f = new LedgerFixture();
    private TransactionId funding;

    @BeforeEach
    void fund() {
        funding = f.post.post(f.fund("pispi-in:1", f.adama, 100_000)).transaction().id();
    }

    @Test
    void reversalUndoesThePostingWithADefaultExternalId() {
        var result = f.reverse.reverse(new ReverseTransactionCommand(funding, Optional.empty(), List.of()));

        assertThat(result).isInstanceOf(PostingResult.Created.class);
        assertThat(result.transaction().externalId()).isEqualTo(ExternalId.of("reversal:" + funding));
        assertThat(result.transaction().reversesId()).contains(funding);
        assertThat(f.posted(f.adama).isZero()).isTrue();
        assertThat(f.posted(f.treasury).isZero()).isTrue();
    }

    @Test
    void retryingTheReversalIsAReplay() {
        var command = new ReverseTransactionCommand(funding, Optional.empty(), List.of());
        var first = f.reverse.reverse(command);

        var retry = f.reverse.reverse(command);

        assertThat(retry).isInstanceOf(PostingResult.Replayed.class);
        assertThat(retry.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(f.posted(f.adama).isZero()).isTrue();
    }

    @Test
    void aTransactionIsReversedOnlyOnce() {
        f.reverse.reverse(new ReverseTransactionCommand(funding, Optional.empty(), List.of()));

        assertThatThrownBy(() -> f.reverse.reverse(
                new ReverseTransactionCommand(funding, Optional.of(ExternalId.of("other")), List.of())))
                .isInstanceOf(TransactionAlreadyReversedException.class);
        assertThat(f.posted(f.adama).isZero()).isTrue();
    }

    @Test
    void reversalCanBeGuardedAgainstOverdraft() {
        f.post.post(f.transfer("p2p:1", f.adama, f.binta, 100_000, POSTED));

        assertThatThrownBy(() -> f.reverse.reverse(new ReverseTransactionCommand(
                funding, Optional.empty(), List.of(LedgerFixture.notOverdrawn(f.adama)))))
                .hasMessageContaining("available >= 0");
        assertThat(f.posted(f.binta)).isEqualTo(Money.xof(100_000));
    }

    @Test
    void pendingTransactionsCannotBeReversed() {
        var hold = f.post.post(f.transfer("cashout:1", f.adama, f.treasury, 10_000, PENDING)).transaction().id();

        assertThatThrownBy(() -> f.reverse.reverse(new ReverseTransactionCommand(hold, Optional.empty(), List.of())))
                .isInstanceOf(TransactionNotReversibleException.class);
    }
}
