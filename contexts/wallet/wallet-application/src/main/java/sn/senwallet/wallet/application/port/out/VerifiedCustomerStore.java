package sn.senwallet.wallet.application.port.out;

import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Wallet;

/** Writes a verified customer and its wallet in one database transaction. */
public interface VerifiedCustomerStore {

    void save(Customer customer, Wallet wallet);
}
