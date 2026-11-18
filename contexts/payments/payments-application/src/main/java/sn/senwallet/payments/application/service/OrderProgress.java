package sn.senwallet.payments.application.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.senwallet.payments.application.port.out.InquirySchedule;
import sn.senwallet.payments.application.port.out.LedgerPort;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.domain.model.PaymentOrder;

/**
 * Ledger transitions for an order. Each method saves after the ledger call.
 * Replaying a posted or archived transaction does not move the order backwards.
 */
public final class OrderProgress {

    private static final Logger log = LoggerFactory.getLogger(OrderProgress.class);

    private final PaymentOrderRepository orders;
    private final LedgerPort ledger;
    private final InquirySchedule schedule;
    private final Clock clock;

    public OrderProgress(
            PaymentOrderRepository orders,
            LedgerPort ledger,
            InquirySchedule schedule,
            Clock clock) {
        this.orders = orders;
        this.ledger = ledger;
        this.schedule = schedule;
        this.clock = clock;
    }

    public PaymentOrder reconcile(PaymentOrder order) {
        if (order.ledgerTxnId() == null || order.state().terminal()) {
            return order;
        }
        var status = ledger.status(order.ledgerTxnId());
        var now = clock.instant();
        if ("posted".equals(status)) {
            var completed = order.state() == PaymentOrder.State.HOLD_PLACED
                    ? order.submitted(order.providerRef(), null, now).completed(now)
                    : order.completed(now);
            orders.update(completed);
            return completed;
        }
        if ("archived".equals(status)) {
            PaymentOrder closed = order.state() == PaymentOrder.State.HOLD_PLACED
                    ? order.rejected(now)
                    : order.failed(now);
            orders.update(closed);
            return closed;
        }
        return order;
    }

    public PaymentOrder place(PaymentOrder order, LedgerPort.Hold hold) {
        var txnId = ledger.place(hold);
        var placed = order.holdPlaced(txnId, clock.instant());
        orders.update(placed);
        return placed;
    }

    public PaymentOrder rejectWithoutHold(PaymentOrder order) {
        var rejected = order.rejected(clock.instant());
        orders.update(rejected);
        return rejected;
    }

    public PaymentOrder rejectRail(PaymentOrder order) {
        if (order.ledgerTxnId() != null) {
            ledger.archive(order.ledgerTxnId());
        }
        var rejected = order.rejected(clock.instant());
        orders.update(rejected);
        return rejected;
    }

    public PaymentOrder unknown(PaymentOrder order) {
        var next = schedule.after(clock.instant(), 0);
        var unknown = order.unknown(next, clock.instant());
        orders.update(unknown);
        return unknown;
    }

    public PaymentOrder pending(PaymentOrder order, String providerRef) {
        var next = schedule.after(clock.instant(), 0);
        var submitted = order.submitted(providerRef, next, clock.instant());
        orders.update(submitted);
        return submitted;
    }

    public PaymentOrder complete(PaymentOrder order, String providerRef) {
        if (order.state() == PaymentOrder.State.COMPLETED) {
            return order;
        }
        if (order.state().terminal()) {
            return order;
        }
        var current = order.providerRef() == null && providerRef != null
                ? order.withProviderRef(providerRef)
                : order;
        if (current.state() == PaymentOrder.State.HOLD_PLACED) {
            current = current.submitted(providerRef, null, clock.instant());
        }
        ledger.post(current.ledgerTxnId());
        var completed = current.completed(clock.instant());
        orders.update(completed);
        return completed;
    }

    public PaymentOrder fail(PaymentOrder order) {
        if (order.state() == PaymentOrder.State.FAILED || order.state() == PaymentOrder.State.REJECTED) {
            return order;
        }
        if (order.state().terminal()) {
            return order;
        }
        if (order.ledgerTxnId() != null) {
            ledger.archive(order.ledgerTxnId());
        }
        var now = clock.instant();
        var failed = order.state() == PaymentOrder.State.HOLD_PLACED ? order.rejected(now) : order.failed(now);
        orders.update(failed);
        return failed;
    }

    public PaymentOrder miss(PaymentOrder order) {
        var now = clock.instant();
        var next = schedule.after(now, order.attempts());
        var updated = order.inquiryMissed(next, schedule.maxAttempts(), now);
        orders.update(updated);
        if (updated.state() == PaymentOrder.State.MANUAL_REVIEW) {
            log.warn("payment order {} needs manual review after {} inquiries", order.id(), updated.attempts());
        }
        return updated;
    }
}
