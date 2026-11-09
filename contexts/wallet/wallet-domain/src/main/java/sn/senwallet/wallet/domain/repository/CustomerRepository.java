package sn.senwallet.wallet.domain.repository;

import java.util.Optional;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Phone;

public interface CustomerRepository {

    void insert(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByPhone(Phone phone);
}
