package sn.senwallet.wallet.application.port.out;

import sn.senwallet.wallet.domain.model.Customer;

/** Persists a tier change and the fact payments consumes, in one transaction. */
public interface TierChangeStore {

    void save(Customer updated);
}
