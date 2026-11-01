package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.senwallet.ledger.domain.model.transaction.TransactionStatus.POSTED;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.exception.IdempotencyConflictException;
import sn.senwallet.ledger.application.exception.LedgerNotFoundException;
import sn.senwallet.ledger.application.port.in.PostingResult;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Money;

class PostTransactionHandlerTest {

    private final LedgerFixture f = new LedgerFixture();

    @Test
    void postsAndReturnsTheNewBalances() {
        var result = f.post.post(f.fund("pispi-in:1", f.adama, 100_000));

        assertThat(result).isInstanceOf(PostingResult.Created.class);
        assertThat(result.transaction().status()).isEqualTo(POSTED);
        assertThat(result.balances())
                .filteredOn(b -> b.accountId().equals(f.adama))
                .singleElement()
                .satisfies(b -> assertThat(b.available()).isEqualTo(Money.xof(100_000)));
        assertThat(f.available(f.adama)).isEqualTo(Money.xof(100_000));
    }

    @Test
    void replayingTheSameRequestReturnsTheStoredTransaction() {
        var command = f.fund("pispi-in:1", f.adama, 100_000);
        var first = f.post.post(command);

        var replay = f.post.post(command);

        assertThat(replay).isInstanceOf(PostingResult.Replayed.class);
        assertThat(replay.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(f.available(f.adama)).isEqualTo(Money.xof(100_000));
        assertThat(f.transactions.findAll()).hasSize(1);
    }

    @Test
    void reusingAnExternalIdForAnotherRequestIsAConflict() {
        f.post.post(f.fund("pispi-in:1", f.adama, 100_000));

        assertThatThrownBy(() -> f.post.post(f.fund("pispi-in:1", f.adama, 100_001)))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("pispi-in:1");
        assertThat(f.available(f.adama)).isEqualTo(Money.xof(100_000));
    }

    @Test
    void violatedConditionLeavesBalancesUntouched() {
        assertThatThrownBy(() -> f.post.post(f.transfer("p2p:1", f.adama, f.binta, 1, POSTED)))
                .isInstanceOf(BalanceConditionViolatedException.class);
        assertThat(f.available(f.adama).isZero()).isTrue();
        assertThat(f.available(f.binta).isZero()).isTrue();
    }

    @Test
    void unknownLedgerIsRefused() {
        var command = f.transfer("x", f.treasury, f.adama, 1, POSTED);
        var foreign = new PostTransactionCommand(LedgerId.newId(), ExternalId.of("x"), POSTED, command.entries(),
                List.of(), null, Map.of());

        assertThatThrownBy(() -> f.post.post(foreign)).isInstanceOf(LedgerNotFoundException.class);
    }
}
