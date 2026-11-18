package sn.senwallet.payments.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import sn.senwallet.payments.application.exception.HoldRejected;
import sn.senwallet.payments.application.exception.PaymentProblem;
import sn.senwallet.payments.application.model.PaymentOutcome;
import sn.senwallet.payments.application.model.StartCommand;
import sn.senwallet.payments.application.model.TopupMonth;
import sn.senwallet.payments.application.port.in.StartPaymentUseCase;
import sn.senwallet.payments.application.port.out.IdempotencyStore;
import sn.senwallet.payments.application.port.out.LedgerPort;
import sn.senwallet.payments.application.port.out.LedgerPort.Condition;
import sn.senwallet.payments.application.port.out.LedgerPort.Hold;
import sn.senwallet.payments.application.port.out.LedgerPort.Line;
import sn.senwallet.payments.application.port.out.LedgerPort.RailAccounts;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.application.port.out.PricingCatalog;
import sn.senwallet.payments.application.port.out.RailPort;
import sn.senwallet.payments.application.port.out.WalletDirectory;
import sn.senwallet.payments.application.port.out.WalletDirectory.ProjectedWallet;
import sn.senwallet.payments.domain.model.PaymentOrder;
import sn.senwallet.payments.domain.model.RailStatus;

public final class StartPaymentHandler implements StartPaymentUseCase {

    private final IdempotencyStore idempotency;
    private final PaymentOrderRepository orders;
    private final WalletDirectory wallets;
    private final PricingCatalog pricing;
    private final LedgerPort ledger;
    private final RailPort rails;
    private final OrderProgress progress;
    private final Clock clock;

    public StartPaymentHandler(
            IdempotencyStore idempotency,
            PaymentOrderRepository orders,
            WalletDirectory wallets,
            PricingCatalog pricing,
            LedgerPort ledger,
            RailPort rails,
            OrderProgress progress,
            Clock clock) {
        this.idempotency = idempotency;
        this.orders = orders;
        this.wallets = wallets;
        this.pricing = pricing;
        this.ledger = ledger;
        this.rails = rails;
        this.progress = progress;
        this.clock = clock;
    }

    @Override
    public PaymentOutcome start(StartCommand command) {
        validate(command);
        var hash = RequestHasher.hash(
                command.type().name(),
                command.walletId().toString(),
                command.provider().name(),
                Long.toString(command.amount()),
                command.currency(),
                blank(command.phone()),
                blank(command.creditorAlias()));
        var begun = idempotency.begin(command.idempotencyKey(), hash);
        if (begun.finished()) {
            return PaymentOutcome.replay(command.idempotencyKey(), begun.responseStatus(), begun.responseBody());
        }
        var existing = orders.findById(begun.orderId());
        if (existing.isPresent()) {
            return resume(command.idempotencyKey(), existing.get());
        }
        var wallet = wallets.find(command.walletId())
                .orElseThrow(() -> PaymentProblem.notFound("wallet " + command.walletId() + " is not projected yet"));
        var now = clock.instant();
        var fee = command.type() == PaymentOrder.Type.CASHOUT ? pricing.cashoutFee(command.amount(), now) : 0L;
        if (fee >= command.amount()) {
            fee = 0L;
        }
        var phone = command.phone() == null || command.phone().isBlank() ? wallet.phone() : command.phone();
        var alias = command.type() == PaymentOrder.Type.PISPI_OUT ? command.creditorAlias() : wallet.alias();
        var order = PaymentOrder.open(begun.orderId(), command.type(), wallet.walletId(), command.amount(), fee,
                command.provider(), phone, alias, now);
        if (command.type() == PaymentOrder.Type.TOPUP) {
            var cap = pricing.monthlyTopupCap(now);
            var inserted = orders.insertWithinMonthlyCap(order, cap, TopupMonth.start(now), TopupMonth.end(now));
            if (!inserted) {
                var detail = "monthly top-up cap of " + cap + " XOF would be exceeded";
                remember(command.idempotencyKey(), 422, "monthly_cap_exceeded", detail, null);
                return PaymentOutcome.problem(command.idempotencyKey(), 422, "monthly_cap_exceeded", detail, null);
            }
        } else {
            orders.insert(order);
        }
        return drive(command.idempotencyKey(), order, wallet);
    }

    @Override
    public PaymentOrder get(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> PaymentProblem.notFound("payment order " + orderId + " does not exist"));
    }

    private PaymentOutcome resume(UUID key, PaymentOrder order) {
        var current = progress.reconcile(order);
        if (current.state().terminal() || current.state() == PaymentOrder.State.SUBMITTED
                || current.state() == PaymentOrder.State.UNKNOWN
                || current.state() == PaymentOrder.State.MANUAL_REVIEW) {
            return finish(key, current, null, null);
        }
        if (current.railSubmitted()) {
            return finish(key, progress.unknown(current), null, null);
        }
        var wallet = wallets.find(current.walletId())
                .orElseThrow(() -> PaymentProblem.notFound("wallet " + current.walletId() + " is not projected yet"));
        if (current.state() == PaymentOrder.State.CREATED) {
            return drive(key, current, wallet);
        }
        return callRail(key, current, wallet);
    }

    private PaymentOutcome drive(UUID key, PaymentOrder order, ProjectedWallet wallet) {
        var accounts = ledger.ensureRails(wallet.ledgerId(), wallet.feeAccountId());
        try {
            var placed = progress.place(order, hold(order, wallet, accounts));
            return callRail(key, placed, wallet);
        } catch (HoldRejected rejected) {
            var closed = progress.rejectWithoutHold(order);
            remember(key, rejected.status(), rejected.code(), rejected.getMessage(), closed);
            return PaymentOutcome.problem(key, rejected.status(), rejected.code(), rejected.getMessage(), closed);
        }
    }

    private PaymentOutcome callRail(UUID key, PaymentOrder order, ProjectedWallet wallet) {
        if (!orders.claimRail(order.id())) {
            var current = orders.findById(order.id()).orElse(order);
            return finish(key, current, null, null);
        }
        var claimed = order.railSubmitted(clock.instant());
        var reply = invoke(claimed, wallet);
        if (reply.rejected()) {
            var closed = progress.rejectRail(claimed);
            var detail = reply.detail() == null ? "rail rejected the order" : reply.detail();
            remember(key, 422, "rail_rejected", detail, closed);
            return PaymentOutcome.problem(key, 422, "rail_rejected", detail, closed);
        }
        if (reply.status() == RailStatus.UNKNOWN) {
            return finish(key, progress.unknown(claimed), null, null);
        }
        if (reply.status() == RailStatus.PENDING) {
            return finish(key, progress.pending(claimed, reply.providerRef()), null, null);
        }
        if (reply.status() == RailStatus.FAILED) {
            var closed = progress.rejectRail(claimed);
            var detail = reply.detail() == null ? "rail failed the order" : reply.detail();
            remember(key, 422, "rail_rejected", detail, closed);
            return PaymentOutcome.problem(key, 422, "rail_rejected", detail, closed);
        }
        return finish(key, progress.complete(claimed, reply.providerRef()), null, null);
    }

    private RailPort.RailReply invoke(PaymentOrder order, ProjectedWallet wallet) {
        var id = order.id().toString();
        return switch (order.type()) {
            case CASHOUT -> rails.disburse(order.provider(), id, order.phone(), order.amount());
            case TOPUP -> rails.collect(order.provider(), id, order.phone(), order.amount());
            case PISPI_OUT -> rails.send(id, wallet.alias(), order.alias(), order.amount());
            case PISPI_IN -> throw new IllegalStateException("inbound credits are not started here");
        };
    }

    private static Hold hold(PaymentOrder order, ProjectedWallet wallet, RailAccounts accounts) {
        var lines = new ArrayList<Line>();
        var conditions = new ArrayList<Condition>();
        var description = switch (order.type()) {
            case CASHOUT -> {
                lines.add(new Line(wallet.ledgerAccountId(), "debit", order.amount()));
                var towardOperator = order.amount() - order.fee();
                lines.add(new Line(accounts.floatOf(order.provider()), "credit", towardOperator));
                if (order.fee() > 0) {
                    lines.add(new Line(accounts.feeAccountId(), "credit", order.fee()));
                }
                conditions.add(new Condition(wallet.ledgerAccountId(), "available", "gte", 0));
                yield "cash-out";
            }
            case TOPUP -> {
                lines.add(new Line(accounts.receivableOf(order.provider()), "debit", order.amount()));
                lines.add(new Line(wallet.ledgerAccountId(), "credit", order.amount()));
                conditions.add(new Condition(wallet.ledgerAccountId(), "pending", "lte", wallet.holdingCap()));
                yield "top-up";
            }
            case PISPI_OUT -> {
                lines.add(new Line(wallet.ledgerAccountId(), "debit", order.amount()));
                lines.add(new Line(accounts.compensationId(), "credit", order.amount()));
                conditions.add(new Condition(wallet.ledgerAccountId(), "available", "gte", 0));
                yield "pispi-out";
            }
            case PISPI_IN -> throw new IllegalStateException("inbound credits are not holds");
        };
        return new Hold(accounts.ledgerId(), order.externalId(), description, lines, conditions);
    }

    private PaymentOutcome finish(UUID key, PaymentOrder order, String code, String detail) {
        var outcome = code == null
                ? PaymentOutcome.accepted(key, order)
                : PaymentOutcome.problem(key, 422, code, detail, order);
        remember(key, outcome.httpStatus(), outcome.code(), outcome.detail(), order);
        return outcome;
    }

    private void remember(UUID key, int status, String code, String detail, PaymentOrder order) {
        var body = code == null ? PaymentBodies.order(order) : PaymentBodies.problem(code, detail, order);
        idempotency.finish(key, status, body);
    }

    private static void validate(StartCommand command) {
        if (command.idempotencyKey() == null) {
            throw PaymentProblem.invalid("Idempotency-Key", "Idempotency-Key is required");
        }
        if (command.walletId() == null) {
            throw PaymentProblem.invalid("wallet_id", "wallet_id is required");
        }
        if (command.amount() <= 0) {
            throw PaymentProblem.invalid("amount", "amount must be positive");
        }
        if (!"XOF".equals(command.currency())) {
            throw PaymentProblem.invalid("currency", "currency must be XOF");
        }
        if (command.type() != PaymentOrder.Type.PISPI_OUT && command.provider() == PaymentOrder.Provider.PISPI) {
            throw PaymentProblem.invalid("provider", "provider must be wave or orange");
        }
        if (command.type() == PaymentOrder.Type.PISPI_OUT
                && (command.creditorAlias() == null || command.creditorAlias().isBlank())) {
            throw PaymentProblem.invalid("creditor_alias", "creditor_alias is required");
        }
        if (command.phone() != null && !command.phone().isBlank()
                && !command.phone().matches("\\+[1-9]\\d{7,14}")) {
            throw PaymentProblem.invalid("phone", "phone must be E.164");
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
