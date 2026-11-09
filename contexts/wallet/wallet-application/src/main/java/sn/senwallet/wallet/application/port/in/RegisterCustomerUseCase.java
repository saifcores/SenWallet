package sn.senwallet.wallet.application.port.in;

import sn.senwallet.wallet.domain.model.Customer;

public interface RegisterCustomerUseCase {

    Customer register(String phone);
}
