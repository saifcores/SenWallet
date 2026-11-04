package sn.senwallet.ledger.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import sn.senwallet.ledger.application.command.CreateLedgerCommand;
import sn.senwallet.ledger.application.command.OpenAccountCommand;
import sn.senwallet.ledger.application.command.PostTransactionCommand;
import sn.senwallet.ledger.application.command.ReverseTransactionCommand;
import sn.senwallet.ledger.application.command.TransitionCommand;
import sn.senwallet.ledger.application.port.in.AccountBalances;
import sn.senwallet.ledger.domain.model.account.BalanceMode;
import sn.senwallet.ledger.domain.model.account.LedgerAccount;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.condition.BalanceCondition;
import sn.senwallet.ledger.domain.model.condition.BalanceType;
import sn.senwallet.ledger.domain.model.condition.Operator;
import sn.senwallet.ledger.domain.model.ledger.Ledger;
import sn.senwallet.ledger.domain.model.ledger.LedgerEnvironment;
import sn.senwallet.ledger.domain.model.transaction.Amount;
import sn.senwallet.ledger.domain.model.transaction.Direction;
import sn.senwallet.ledger.domain.model.transaction.Entry;
import sn.senwallet.ledger.domain.model.transaction.ExternalId;
import sn.senwallet.ledger.domain.model.transaction.LedgerTransaction;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.AccountResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.BalancesResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.ConditionRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.CreateLedgerRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.EntryRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.EntryResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.LedgerResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.OpenAccountRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.PostTransactionRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.ReversalRequest;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.TransactionResponse;
import sn.senwallet.ledger.infrastructure.adapter.in.web.LedgerDtos.TransitionRequest;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.shared.id.TransactionId;
import sn.senwallet.shared.money.Currency;
import sn.senwallet.shared.money.Money;

/** Requests to commands and results to responses; missing or malformed fields are 400s. */
final class LedgerWebMapper {

    private LedgerWebMapper() {
    }

    static CreateLedgerCommand toCommand(CreateLedgerRequest body) {
        requireBody(body);
        return new CreateLedgerCommand(text("name", body.name()),
                label(LedgerEnvironment.class, "environment", body.environment()));
    }

    static OpenAccountCommand toCommand(OpenAccountRequest body) {
        requireBody(body);
        return new OpenAccountCommand(
                new LedgerId(required("ledger_id", body.ledgerId())),
                text("name", body.name()),
                label(NormalBalance.class, "normal_balance", body.normalBalance()),
                currency(body.currency()),
                body.balanceMode() == null ? BalanceMode.SYNC
                        : label(BalanceMode.class, "balance_mode", body.balanceMode()),
                body.metadata());
    }

    static PostTransactionCommand toCommand(PostTransactionRequest body) {
        requireBody(body);
        var currency = currency(body.currency());
        var status = body.status() == null ? TransactionStatus.POSTED
                : label(TransactionStatus.class, "status", body.status());
        if (status == TransactionStatus.ARCHIVED) {
            throw new InvalidRequestException("status", "must be pending or posted");
        }
        var entries = new ArrayList<Entry>();
        var requested = required("entries", body.entries());
        for (int i = 0; i < requested.size(); i++) {
            entries.add(entry("entries[" + i + "]", requested.get(i), currency));
        }
        return new PostTransactionCommand(
                new LedgerId(required("ledger_id", body.ledgerId())),
                ExternalId.of(text("external_id", body.externalId())),
                status,
                entries,
                conditions(body.conditions(), currency),
                body.description(),
                body.metadata());
    }

    static TransitionCommand toCommand(UUID transactionId, TransitionRequest body) {
        requireBody(body);
        var target = label(TransactionStatus.class, "status", body.status());
        if (target == TransactionStatus.PENDING) {
            throw new InvalidRequestException("status", "must be posted or archived");
        }
        return new TransitionCommand(new TransactionId(transactionId), target);
    }

    static ReverseTransactionCommand toCommand(UUID transactionId, ReversalRequest body) {
        var externalId = body == null || body.externalId() == null ? Optional.<ExternalId>empty()
                : Optional.of(ExternalId.of(text("external_id", body.externalId())));
        return new ReverseTransactionCommand(new TransactionId(transactionId), externalId,
                body == null ? List.of() : conditions(body.conditions(), Currency.XOF));
    }

    static LedgerResponse toResponse(Ledger ledger) {
        return new LedgerResponse(ledger.id().value(), ledger.name(), lower(ledger.environment()),
                ledger.createdAt());
    }

    static AccountResponse toResponse(LedgerAccount account) {
        return new AccountResponse(account.id().value(), account.ledgerId().value(), account.name(),
                lower(account.normalBalance()), account.currency().name(), lower(account.balanceMode()),
                lower(account.status()), account.metadata(), toResponse(AccountBalances.of(account), null));
    }

    static BalancesResponse toResponse(AccountBalances balances, Instant asOf) {
        return new BalancesResponse(balances.accountId().value(), balances.posted().toMinorUnits(),
                balances.pending().toMinorUnits(), balances.available().toMinorUnits(), balances.lockVersion(),
                asOf);
    }

    static TransactionResponse toResponse(LedgerTransaction txn, List<AccountBalances> balances) {
        return new TransactionResponse(
                txn.id().value(),
                txn.ledgerId().value(),
                txn.externalId().value(),
                lower(txn.status()),
                txn.currency().name(),
                txn.description().orElse(null),
                txn.metadata(),
                txn.reversesId().map(TransactionId::value).orElse(null),
                txn.effectiveAt(),
                txn.postedAt().orElse(null),
                txn.archivedAt().orElse(null),
                txn.entries().stream()
                        .map(e -> new EntryResponse(e.accountId().value(), lower(e.direction()),
                                e.amount().value().toMinorUnits()))
                        .toList(),
                balances == null ? null : balances.stream().map(b -> toResponse(b, null)).toList());
    }

    private static Entry entry(String field, EntryRequest entry, Currency currency) {
        if (entry == null) {
            throw new InvalidRequestException(field, "must not be null");
        }
        var amount = required(field + ".amount", entry.amount());
        if (amount <= 0) {
            throw new InvalidRequestException(field + ".amount", "must be > 0");
        }
        return new Entry(new AccountId(required(field + ".account_id", entry.accountId())),
                label(Direction.class, field + ".direction", entry.direction()),
                Amount.of(money(amount, currency)));
    }

    private static List<BalanceCondition> conditions(List<ConditionRequest> requested, Currency currency) {
        if (requested == null) {
            return List.of();
        }
        var conditions = new ArrayList<BalanceCondition>();
        for (int i = 0; i < requested.size(); i++) {
            var field = "conditions[" + i + "]";
            var condition = requested.get(i);
            if (condition == null) {
                throw new InvalidRequestException(field, "must not be null");
            }
            conditions.add(BalanceCondition.of(
                    new AccountId(required(field + ".account_id", condition.accountId())),
                    label(BalanceType.class, field + ".balance", condition.balance()),
                    label(Operator.class, field + ".operator", condition.operator()),
                    money(required(field + ".amount", condition.amount()), currency)));
        }
        return conditions;
    }

    private static Money money(long minorUnits, Currency currency) {
        return Money.of(BigDecimal.valueOf(minorUnits, currency.exponent()), currency);
    }

    private static Currency currency(String code) {
        return code == null ? Currency.XOF : label(Currency.class, "currency", code);
    }

    private static <E extends Enum<E>> E label(Class<E> type, String field, String value) {
        var text = text(field, value);
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(field, "unknown value '" + text + "'");
        }
    }

    private static String text(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(field, "is required");
        }
        return value;
    }

    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new InvalidRequestException(field, "is required");
        }
        return value;
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw new InvalidRequestException("body", "is required");
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
