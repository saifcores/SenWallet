package sn.senwallet.wallet.application.service;

import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.in.ChangeTierUseCase;
import sn.senwallet.wallet.application.port.out.TierChangeStore;
import sn.senwallet.wallet.domain.exception.CustomerNotVerifiedException;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.repository.CustomerRepository;

public final class ChangeTierHandler implements ChangeTierUseCase {

    private final CustomerRepository customers;
    private final TierChangeStore tiers;

    public ChangeTierHandler(CustomerRepository customers, TierChangeStore tiers) {
        this.customers = customers;
        this.tiers = tiers;
    }

    @Override
    public Customer changeTier(CustomerId customerId, KycTier tier) {
        if (tier == null) {
            throw WalletProblem.invalid("tier", "tier is required");
        }
        var customer = customers.findById(customerId)
                .orElseThrow(() -> WalletProblem.notFound("customer " + customerId + " does not exist"));
        Customer updated;
        try {
            updated = customer.withTier(tier);
        } catch (CustomerNotVerifiedException e) {
            throw new WalletProblem(409, "not_verified", e.getMessage());
        }
        tiers.save(updated);
        return updated;
    }
}
