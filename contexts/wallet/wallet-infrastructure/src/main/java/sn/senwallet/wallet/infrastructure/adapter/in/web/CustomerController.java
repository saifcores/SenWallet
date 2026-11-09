package sn.senwallet.wallet.infrastructure.adapter.in.web;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.in.ChangeTierUseCase;
import sn.senwallet.wallet.application.port.in.RegisterCustomerUseCase;
import sn.senwallet.wallet.application.port.in.VerifyCustomerUseCase;
import sn.senwallet.wallet.domain.model.KycTier;
import sn.senwallet.wallet.domain.repository.CustomerRepository;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.CustomerResponse;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.KycRequest;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.KycResponse;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.RegisterRequest;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.VerificationResponse;
import sn.senwallet.wallet.infrastructure.adapter.in.web.WalletDtos.VerifyRequest;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final RegisterCustomerUseCase register;
    private final VerifyCustomerUseCase verify;
    private final ChangeTierUseCase changeTier;
    private final CustomerRepository customers;

    public CustomerController(
            RegisterCustomerUseCase register,
            VerifyCustomerUseCase verify,
            ChangeTierUseCase changeTier,
            CustomerRepository customers) {
        this.register = register;
        this.verify = verify;
        this.changeTier = changeTier;
        this.customers = customers;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(@RequestBody(required = false) RegisterRequest body) {
        var customer = register.register(body == null ? null : body.phone());
        return ResponseEntity.created(URI.create("/customers/" + customer.id()))
                .body(new CustomerResponse(
                        customer.id().value(),
                        customer.phone().value(),
                        customer.status().name().toLowerCase(Locale.ROOT),
                        customer.otp()));
    }

    @PostMapping("/{id}/verification")
    public ResponseEntity<VerificationResponse> verify(@PathVariable UUID id,
            @RequestBody(required = false) VerifyRequest body) {
        var wallet = verify.verify(new CustomerId(id), body == null ? null : body.otp());
        var customer = customers.findById(wallet.customerId()).orElseThrow();
        return ResponseEntity.created(URI.create("/wallets/" + wallet.id()))
                .body(new VerificationResponse(
                        wallet.customerId().value(),
                        customer.tier().name().toLowerCase(Locale.ROOT),
                        wallet.id().value(),
                        wallet.alias().value(),
                        wallet.ledgerAccountId().value()));
    }

    @PostMapping("/{id}/kyc")
    public KycResponse changeTier(@PathVariable UUID id, @RequestBody(required = false) KycRequest body) {
        if (body == null || body.tier() == null || body.tier().isBlank()) {
            throw WalletProblem.invalid("tier", "tier is required");
        }
        KycTier tier;
        try {
            tier = KycTier.valueOf(body.tier().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw WalletProblem.invalid("tier", "tier must be n1, n2 or merchant");
        }
        var customer = changeTier.changeTier(new CustomerId(id), tier);
        return new KycResponse(customer.id().value(), customer.tier().name().toLowerCase(Locale.ROOT));
    }
}
