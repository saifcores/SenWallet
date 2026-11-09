package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.wallet.application.port.out.VerifiedCustomerStore;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;

@Repository
public class JdbcVerifiedCustomerStore implements VerifiedCustomerStore {

    private final CustomerRepository customers;
    private final WalletRepository wallets;

    public JdbcVerifiedCustomerStore(CustomerRepository customers, WalletRepository wallets) {
        this.customers = customers;
        this.wallets = wallets;
    }

    @Override
    @Transactional
    public void save(Customer customer, Wallet wallet) {
        customers.update(customer);
        wallets.insert(wallet);
    }
}
