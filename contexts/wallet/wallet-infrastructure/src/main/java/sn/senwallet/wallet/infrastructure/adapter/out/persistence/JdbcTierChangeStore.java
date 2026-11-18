package sn.senwallet.wallet.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sn.senwallet.wallet.application.port.out.PricingCatalog;
import sn.senwallet.wallet.application.port.out.TierChangeStore;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;
import sn.senwallet.wallet.infrastructure.adapter.out.outbox.JdbcWalletOutbox;

@Repository
public class JdbcTierChangeStore implements TierChangeStore {

    private final CustomerRepository customers;
    private final WalletRepository wallets;
    private final PricingCatalog pricing;
    private final JdbcWalletOutbox outbox;
    private final Clock clock;

    public JdbcTierChangeStore(
            CustomerRepository customers,
            WalletRepository wallets,
            PricingCatalog pricing,
            JdbcWalletOutbox outbox,
            Clock clock) {
        this.customers = customers;
        this.wallets = wallets;
        this.pricing = pricing;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(Customer updated) {
        customers.update(updated);
        var wallet = wallets.findByCustomer(updated.id()).orElse(null);
        if (wallet == null) {
            return;
        }
        var cap = pricing.limit(updated.tier(), clock.instant()).holdingCap().toMinorUnits();
        outbox.tierChanged(wallet, updated.tier().name().toLowerCase(Locale.ROOT), cap);
    }
}
