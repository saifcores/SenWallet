package sn.senwallet.payments.application.model;

import java.util.UUID;
import sn.senwallet.payments.domain.model.PaymentOrder;

public record StartCommand(
        UUID idempotencyKey,
        UUID walletId,
        PaymentOrder.Type type,
        PaymentOrder.Provider provider,
        long amount,
        String currency,
        String phone,
        String creditorAlias) {
}
