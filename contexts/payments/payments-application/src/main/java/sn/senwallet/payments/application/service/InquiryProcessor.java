package sn.senwallet.payments.application.service;

import sn.senwallet.payments.application.port.out.PaymentOrderRepository;
import sn.senwallet.payments.application.port.out.RailPort;
import sn.senwallet.payments.domain.model.RailStatus;

public final class InquiryProcessor {

    private final PaymentOrderRepository orders;
    private final RailPort rails;
    private final OrderProgress progress;

    public InquiryProcessor(PaymentOrderRepository orders, RailPort rails, OrderProgress progress) {
        this.orders = orders;
        this.rails = rails;
        this.progress = progress;
    }

    public void drain() {
        for (var order : orders.lockDue(20)) {
            if (order.providerRef() == null || order.providerRef().isBlank()) {
                progress.miss(order);
                continue;
            }
            var status = rails.poll(order.provider(), order.providerRef());
            if (status == RailStatus.SUCCEEDED) {
                progress.complete(order, order.providerRef());
            } else if (status == RailStatus.FAILED) {
                progress.fail(order);
            } else {
                progress.miss(order);
            }
        }
    }
}
