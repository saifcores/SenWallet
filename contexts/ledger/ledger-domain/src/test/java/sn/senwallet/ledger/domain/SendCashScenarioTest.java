package sn.senwallet.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sn.senwallet.ledger.domain.event.TransactionPending;
import sn.senwallet.ledger.domain.event.TransactionPosted;
import sn.senwallet.ledger.domain.exception.BalanceConditionViolatedException;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerTransactionRepository;
import sn.senwallet.ledger.domain.service.PostingService;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

/**
 * Exercise 2 (ledger part of SendCash): Adama receives 100 000 XOF by PI-SPI,
 * sends 50 000 to Binta (500 fee), and Binta cashes out 50 000 with Orange.
 */
class SendCashScenarioTest {

    private static final LedgerId LEDGER = LedgerId.newId();

    private static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");

    private final InMemoryLedgerAccountRepository accounts = new InMemoryLedgerAccountRepository();
    private final InMemoryLedgerTransactionRepository transactions = new InMemoryLedgerTransactionRepository();
    private final PostingService posting = new PostingService();

    private AccountId adama;
    private AccountId binta;
    private AccountId pispiSettlement;
    private AccountId p2pFees;
    private AccountId orangeFloat;

    @BeforeEach
    void openAccounts() {
        adama = open("Wallet Adama", NormalBalance.CREDIT, BalanceMode.SYNC);
        binta = open("Wallet Binta", NormalBalance.CREDIT, BalanceMode.SYNC);
        pispiSettlement = open("Compensation PI-SPI", NormalBalance.DEBIT, BalanceMode.SYNC);
        p2pFees = open("Revenus - frais P2P", NormalBalance.CREDIT, BalanceMode.ASYNC);
        orangeFloat = open("Float Orange Money", NormalBalance.DEBIT, BalanceMode.SYNC);
    }

    @Test
    void sendCash() {
        // 1. Incoming PI-SPI transfer, posted directly by the webhook.
        post("pispi-in:E2E-0001", TransactionStatus.POSTED, List.of(),
                Entry.debit(pispiSettlement, Amount.xof(100_000)),
                Entry.credit(adama, Amount.xof(100_000)));

        assertBalances(adama, 100_000, 100_000, 100_000);
        assertBalances(pispiSettlement, 100_000, 100_000, 100_000);
        assertTrialBalance();

        // 2. P2P 50 000 to Binta with a 1 % fee, guarded against overdraft.
        post("p2p:T-0001", TransactionStatus.POSTED, List.of(notOverdrawn(adama)),
                Entry.debit(adama, Amount.xof(50_500)),
                Entry.credit(binta, Amount.xof(50_000)),
                Entry.credit(p2pFees, Amount.xof(500)));

        assertBalances(adama, 49_500, 49_500, 49_500);
        assertBalances(binta, 50_000, 50_000, 50_000);
        assertBalances(p2pFees, 500, 500, 500);
        assertTrialBalance();

        // 3. Orange cash-out: funds are held while the operator processes it.
        var cashOut = post("cashout:O-0001", TransactionStatus.PENDING, List.of(notOverdrawn(binta)),
                Entry.debit(binta, Amount.xof(50_000)),
                Entry.credit(orangeFloat, Amount.xof(50_000)));

        assertBalances(binta, 50_000, 0, 0);
        assertBalances(orangeFloat, 0, -50_000, -50_000);
        assertThat(cashOut.pullEvents()).singleElement().isInstanceOf(TransactionPending.class);
        assertTrialBalance();

        // 4. A further cash-out of 1 XOF would overdraw Binta: rejected, nothing
        // written.
        assertThatThrownBy(() -> post("cashout:O-0002", TransactionStatus.PENDING, List.of(notOverdrawn(binta)),
                Entry.debit(binta, Amount.xof(1)),
                Entry.credit(orangeFloat, Amount.xof(1))))
                .isInstanceOf(BalanceConditionViolatedException.class)
                .hasMessageContaining("available >= 0 XOF on " + binta)
                .hasMessageContaining("-1 XOF");
        assertThat(transactions.findByExternalId(ExternalId.of("cashout:O-0002"))).isEmpty();
        assertBalances(binta, 50_000, 0, 0);
        assertBalances(orangeFloat, 0, -50_000, -50_000);

        // 5. Orange confirms the disbursement.
        settle(ExternalId.of("cashout:O-0001"));

        assertBalances(binta, 0, 0, 0);
        assertBalances(orangeFloat, -50_000, -50_000, -50_000);
        assertBalances(adama, 49_500, 49_500, 49_500);
        assertThat(transactions.findByExternalId(ExternalId.of("cashout:O-0001")).orElseThrow().status())
                .isEqualTo(TransactionStatus.POSTED);
        assertTrialBalance();
    }

    private AccountId open(String name, NormalBalance normal, BalanceMode mode) {
        var account = LedgerAccount.open(AccountId.newId(), LEDGER, name, normal, Currency.XOF, mode);
        accounts.save(account);
        return account.id();
    }

    private LedgerTransaction post(
            String externalId, TransactionStatus status, List<BalanceCondition> conditions, Entry... entries) {
        var txn = LedgerTransaction.open(LEDGER, ExternalId.of(externalId), List.of(entries), status, T0);
        var loaded = accounts.lockForUpdateSorted(txn.accountIds());
        posting.apply(txn, loaded, conditions);
        accounts.saveAll(loaded.values());
        transactions.save(txn);
        return txn;
    }

    private void settle(ExternalId externalId) {
        var txn = transactions.findByExternalId(externalId).orElseThrow();
        txn.pullEvents();
        var loaded = accounts.lockForUpdateSorted(txn.accountIds());
        posting.settle(txn, loaded, T0.plusSeconds(120));
        accounts.saveAll(loaded.values());
        transactions.save(txn);
        assertThat(txn.pullEvents()).singleElement().isInstanceOf(TransactionPosted.class);
    }

    private static BalanceCondition notOverdrawn(AccountId account) {
        return BalanceCondition.of(account, BalanceType.AVAILABLE, Operator.GTE, Money.zero(Currency.XOF));
    }

    private void assertBalances(AccountId id, long posted, long pending, long available) {
        var account = accounts.findById(id).orElseThrow();
        assertThat(account.posted()).as("posted of %s", account.name()).isEqualTo(Money.xof(posted));
        assertThat(account.pending()).as("pending of %s", account.name()).isEqualTo(Money.xof(pending));
        assertThat(account.available()).as("available of %s", account.name()).isEqualTo(Money.xof(available));
    }

    private void assertTrialBalance() {
        var debitNormal = Money.zero(Currency.XOF);
        var creditNormal = Money.zero(Currency.XOF);
        for (var account : accounts.findAll()) {
            if (account.normalBalance() == NormalBalance.DEBIT) {
                debitNormal = debitNormal.plus(account.pending());
            } else {
                creditNormal = creditNormal.plus(account.pending());
            }
        }
        assertThat(debitNormal).as("debit-normal vs credit-normal balances").isEqualTo(creditNormal);
    }
}
