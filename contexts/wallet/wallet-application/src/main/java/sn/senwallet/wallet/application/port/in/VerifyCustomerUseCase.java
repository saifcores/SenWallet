package sn.senwallet.wallet.application.port.in;

import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.domain.model.Wallet;

public interface VerifyCustomerUseCase {

    Wallet verify(CustomerId customerId, String otp);
}
