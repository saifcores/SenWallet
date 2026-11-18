package sn.senwallet.payments.application.port.out;

import sn.senwallet.payments.domain.model.PaymentOrder;
import sn.senwallet.payments.domain.model.RailStatus;

public interface RailPort {

    RailReply disburse(PaymentOrder.Provider provider, String orderId, String phone, long amount);

    RailReply collect(PaymentOrder.Provider provider, String orderId, String phone, long amount);

    RailReply send(String orderId, String debtorAlias, String creditorAlias, long amount);

    RailStatus poll(PaymentOrder.Provider provider, String providerRef);

    record RailReply(RailStatus status, String providerRef, boolean rejected, String detail) {

        public static RailReply timeout() {
            return new RailReply(RailStatus.UNKNOWN, null, false, "rail timed out");
        }

        public static RailReply rejected(String detail) {
            return new RailReply(RailStatus.FAILED, null, true, detail);
        }
    }
}
