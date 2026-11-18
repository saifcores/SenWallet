package sn.senwallet.payments.application.port.in;

import java.util.UUID;
import sn.senwallet.payments.application.model.PaymentOutcome;
import sn.senwallet.payments.application.model.StartCommand;
import sn.senwallet.payments.domain.model.PaymentOrder;

public interface StartPaymentUseCase {

    PaymentOutcome start(StartCommand command);

    PaymentOrder get(UUID orderId);
}
