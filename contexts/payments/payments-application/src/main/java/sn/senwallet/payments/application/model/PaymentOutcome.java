package sn.senwallet.payments.application.model;

import java.util.UUID;
import sn.senwallet.payments.domain.model.PaymentOrder;

/** What {@code POST} returns. {@code replayBody} is set when the key was already finished. */
public record PaymentOutcome(
        UUID idempotencyKey,
        int httpStatus,
        String code,
        String detail,
        PaymentOrder order,
        String replayBody) {

    public static PaymentOutcome replay(UUID key, int httpStatus, String body) {
        return new PaymentOutcome(key, httpStatus, null, null, null, body);
    }

    public static PaymentOutcome accepted(UUID key, PaymentOrder order) {
        var status = order.state() == PaymentOrder.State.COMPLETED ? 201 : 202;
        return new PaymentOutcome(key, status, null, null, order, null);
    }

    public static PaymentOutcome problem(UUID key, int httpStatus, String code, String detail, PaymentOrder order) {
        return new PaymentOutcome(key, httpStatus, code, detail, order, null);
    }
}
