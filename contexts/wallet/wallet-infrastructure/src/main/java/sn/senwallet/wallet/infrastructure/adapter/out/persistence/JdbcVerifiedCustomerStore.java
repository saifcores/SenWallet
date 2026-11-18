package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.application.port.out.VerifiedCustomerStore;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;
import sn.senwallet.wallet.infrastructure.adapter.out.outbox.JdbcWalletOutbox;

@Repository
public class JdbcVerifiedCustomerStore implements VerifiedCustomerStore {

    private final CustomerRepository customers;
    private final WalletRepository wallets;
    private final JdbcClient jdbc;
    private final PricingCatalog pricing;
    private final JdbcWalletOutbox outbox;
    private final java.time.Clock clock;

    public JdbcVerifiedCustomerStore(
            CustomerRepository customers,
            WalletRepository wallets,
            JdbcClient jdbc,
            PricingCatalog pricing,
            JdbcWalletOutbox outbox,
            java.time.Clock clock) {
        this.customers = customers;
        this.wallets = wallets;
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(Customer customer, Wallet wallet) {
        customers.update(customer);
        wallets.insert(wallet);
        var platform = jdbc.sql("SELECT ledger_id, fee_account_id FROM platform_accounts")
                .query((rs, row) -> new UUID[] {
                        rs.getObject("ledger_id", UUID.class),
                        rs.getObject("fee_account_id", UUID.class)
                })
                .single();
        var cap = pricing.limit(customer.tier(), clock.instant()).holdingCap().toMinorUnits();
        outbox.walletCreated(customer, wallet, platform[0], platform[1], cap);
    }
}
