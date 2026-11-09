package sn.senwallet.wallet.application.service;

import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.in.ChangeTierUseCase;
import sn.senwallet.wallet.domain.exception.CustomerNotVerifiedException;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.repository.CustomerRepository;

public final class ChangeTierHandler implements ChangeTierUseCase {

    private final CustomerRepository customers;

    public ChangeTierHandler(CustomerRepository customers) {
        this.customers = customers;
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
        customers.update(updated);
        return updated;
    }
}
