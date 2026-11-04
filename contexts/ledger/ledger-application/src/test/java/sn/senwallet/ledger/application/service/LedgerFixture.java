package sn.senwallet.ledger.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import sn.senwallet.ledger.application.command.CreateLedgerCommand;
import sn.senwallet.ledger.application.command.OpenAccountCommand;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.port.out.ClockPort;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.ledger.LedgerEnvironment;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerAccountRepository;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerRepository;
import sn.senwallet.ledger.domain.repository.inmemory.InMemoryLedgerTransactionRepository;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

/** In-memory ledger with a treasury (debit-normal) and two wallets. */
final class LedgerFixture {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    final InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
    final InMemoryLedgerAccountRepository accounts = new InMemoryLedgerAccountRepository();
    final InMemoryLedgerTransactionRepository transactions = new InMemoryLedgerTransactionRepository();
    final ClockPort clock = () -> NOW;

    final PostTransactionHandler post = new PostTransactionHandler(ledgers, transactions, accounts, clock);
    final TransitionHandler transition = new TransitionHandler(transactions, accounts, clock);
    final ReverseHandler reverse = new ReverseHandler(transactions, accounts, clock);

    final LedgerId ledger;
    final AccountId treasury;
    final AccountId adama;
    final AccountId binta;

    LedgerFixture() {
        ledger = new CreateLedgerHandler(ledgers, clock)
                .create(new CreateLedgerCommand("senwallet", LedgerEnvironment.TEST)).id();
        var open = new OpenAccountHandler(ledgers, accounts);
        treasury = open.open(account("treasury", NormalBalance.DEBIT)).id();
        adama = open.open(account("wallet adama", NormalBalance.CREDIT)).id();
        binta = open.open(account("wallet binta", NormalBalance.CREDIT)).id();
    }

    OpenAccountCommand account(String name, NormalBalance normal) {
        return new OpenAccountCommand(ledger, name, normal, Currency.XOF, BalanceMode.SYNC, Map.of());
    }

    PostTransactionCommand transfer(
            String externalId, AccountId from, AccountId to, long amount, TransactionStatus status) {
        return new PostTransactionCommand(ledger, ExternalId.of(externalId), status,
                List.of(Entry.debit(from, Amount.xof(amount)), Entry.credit(to, Amount.xof(amount))),
                List.of(notOverdrawn(from)), null, Map.of());
    }

    /** Treasury to wallet, posted, without condition. */
    PostTransactionCommand fund(String externalId, AccountId wallet, long amount) {
        return new PostTransactionCommand(ledger, ExternalId.of(externalId), TransactionStatus.POSTED,
                List.of(Entry.debit(treasury, Amount.xof(amount)), Entry.credit(wallet, Amount.xof(amount))),
                List.of(), null, Map.of());
    }

    static BalanceCondition notOverdrawn(AccountId account) {
        return BalanceCondition.of(account, BalanceType.AVAILABLE, Operator.GTE, Money.zero(Currency.XOF));
    }

    Money available(AccountId account) {
        return accounts.findById(account).orElseThrow().available();
    }

    Money posted(AccountId account) {
        return accounts.findById(account).orElseThrow().posted();
    }
}
