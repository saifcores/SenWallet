package sn.senwallet.wallet.application.service;

import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.shared.id.WalletId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.in.VerifyCustomerUseCase;
import sn.senwallet.wallet.application.port.out.LedgerGateway;
import sn.senwallet.wallet.application.port.out.VerifiedCustomerStore;
import sn.senwallet.wallet.domain.exception.InvalidOtpException;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Wallet;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.domain.repository.WalletRepository;

public final class VerifyCustomerHandler implements VerifyCustomerUseCase {

    private final CustomerRepository customers;
    private final WalletRepository wallets;
    private final VerifiedCustomerStore store;
    private final LedgerGateway ledger;

    public VerifyCustomerHandler(
            CustomerRepository customers,
            WalletRepository wallets,
            VerifiedCustomerStore store,
            LedgerGateway ledger) {
        this.customers = customers;
        this.wallets = wallets;
        this.store = store;
        this.ledger = ledger;
    }

    @Override
    public Wallet verify(CustomerId customerId, String otp) {
        var customer = customers.findById(customerId)
                .orElseThrow(() -> WalletProblem.notFound("customer " + customerId + " does not exist"));
        var existing = wallets.findByCustomer(customerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (otp == null || otp.isBlank()) {
            throw WalletProblem.invalid("otp", "otp is required");
        }
        Customer verified;
        try {
            verified = customer.verify(otp);
        } catch (InvalidOtpException e) {
            throw WalletProblem.invalid("otp", e.getMessage());
        }
        var platform = ledger.ensurePlatform();
        var accountId = ledger.openWalletAccount(platform.ledgerId(), "Wallet " + customer.phone().value());
        var wallet = Wallet.active(WalletId.newId(), customerId, accountId, customer.phone());
        store.save(verified, wallet);
        return wallet;
    }
}
