package sn.senwallet.payments.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sn.senwallet.payments.application.port.out.CallbackInbox;
import sn.senwallet.payments.application.port.out.CallbackInbox.StoredCallback;
import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.domain.model.CallbackPreference;
import sn.senwallet.payments.domain.model.PaymentOrder;
import sn.senwallet.payments.domain.model.RailStatus;

/** Applies a locked inbox batch. A success wins when a failure is still unprocessed. */
public final class CallbackProcessor {

    private final CallbackInbox inbox;
    private final PaymentOrderRepository orders;
    private final OrderProgress progress;
    private final InboundCredit inbound;

    public CallbackProcessor(
            CallbackInbox inbox,
            PaymentOrderRepository orders,
            OrderProgress progress,
            InboundCredit inbound) {
        this.inbox = inbox;
        this.orders = orders;
        this.progress = progress;
        this.inbound = inbound;
    }

    public void drain() {
        var batch = inbox.lockPending(50);
        if (batch.isEmpty()) {
            return;
        }
        var groups = new LinkedHashMap<String, List<StoredCallback>>();
        for (var callback : batch) {
            groups.computeIfAbsent(callback.provider() + "|" + correlation(callback), ignored -> new ArrayList<>())
                    .add(callback);
        }
        for (var group : groups.values()) {
            apply(group);
            inbox.markProcessed(group.stream().map(StoredCallback::id).toList());
        }
    }

    private void apply(List<StoredCallback> group) {
        var chosen = CallbackPreference.prefer(group.stream().map(callback -> RailStatus.parse(callback.status())).toList());
        var sample = group.getFirst();
        var order = locate(sample);
        if (order.isEmpty()) {
            if ("pispi".equals(sample.provider()) && chosen == RailStatus.SUCCEEDED && sample.amount() > 0) {
                inbound.credit(sample.externalRef(), sample.creditorAlias(), sample.amount());
            }
            return;
        }
        var current = progress.reconcile(order.get());
        if (chosen == RailStatus.SUCCEEDED) {
            progress.complete(current, firstRef(group));
        } else if (chosen == RailStatus.FAILED) {
            progress.fail(current);
        }
    }

    private Optional<PaymentOrder> locate(StoredCallback callback) {
        if (callback.externalRef() != null) {
            try {
                var byId = orders.findById(UUID.fromString(callback.externalRef()));
                if (byId.isPresent()) {
                    return byId;
                }
            } catch (IllegalArgumentException ignored) {
                // PI-SPI end-to-end ids are not always order ids.
            }
        }
        if (callback.providerRef() == null) {
            return Optional.empty();
        }
        return orders.findByProviderRef(callback.providerRef());
    }

    private static String correlation(StoredCallback callback) {
        if (callback.externalRef() != null && !callback.externalRef().isBlank()) {
            return callback.externalRef();
        }
        return callback.providerRef() == null ? Long.toString(callback.id()) : callback.providerRef();
    }

    private static String firstRef(List<StoredCallback> group) {
        for (var callback : group) {
            if (callback.providerRef() != null) {
                return callback.providerRef();
            }
        }
        return null;
    }
}
