package sn.senwallet.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.application.exception.AccountNotFoundException;
import sn.senwallet.ledger.application.exception.TransactionNotFoundException;
import sn.senwallet.ledger.domain.model.account.Balances;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

class LedgerQueryServiceTest {

    private final LedgerFixture f = new LedgerFixture();
    private final AtomicReference<Instant> askedAt = new AtomicReference<>();
    private final LedgerQueryService queries = new LedgerQueryService(f.ledgers, f.accounts, f.transactions,
            (account, asOf) -> {
                askedAt.set(asOf);
                return Balances.zero(Currency.XOF)
                        .withPosted(Direction.CREDIT, Amount.xof(500))
                        .withPending(Direction.DEBIT, Amount.xof(200));
            });

    @Test
    void currentBalancesComeFromTheCounters() {
        f.post.post(f.fund("pispi-in:1", f.adama, 1_000));

        var balances = queries.current(f.adama);

        assertThat(balances.posted()).isEqualTo(Money.xof(1_000));
        assertThat(balances.lockVersion()).isEqualTo(1);
    }

    @Test
    void historicalBalancesUseTheAccountsNormalBalance() {
        var asOf = Instant.parse("2026-11-01T00:00:00Z");

        var balances = queries.asOf(f.adama, asOf);

        assertThat(askedAt).hasValue(asOf);
        assertThat(balances.posted()).isEqualTo(Money.xof(500));
        assertThat(balances.pending()).isEqualTo(Money.xof(300));
        assertThat(balances.available()).isEqualTo(Money.xof(300));
    }

    @Test
    void lookupsThrowWhenMissing() {
        assertThatThrownBy(() -> queries.account(AccountId.newId())).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> queries.transaction(TransactionId.newId()))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThat(queries.ledger(f.ledger).name()).isEqualTo("senwallet");
    }
}
