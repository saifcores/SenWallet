package sn.senwallet.wallet.application.service;

import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.application.exception.WalletProblem;
import sn.senwallet.wallet.application.port.in.RegisterCustomerUseCase;
import sn.senwallet.wallet.domain.model.Customer;
import sn.senwallet.wallet.domain.model.Phone;
import sn.senwallet.wallet.domain.repository.CustomerRepository;

public final class RegisterCustomerHandler implements RegisterCustomerUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterCustomerHandler.class);

    private final CustomerRepository customers;
    private final SecureRandom random = new SecureRandom();

    public RegisterCustomerHandler(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public Customer register(String phoneNumber) {
        var phone = phone(phoneNumber);
        if (customers.findByPhone(phone).isPresent()) {
            throw new WalletProblem(409, "phone_already_registered", "phone is already registered");
        }
        var otp = "%06d".formatted(random.nextInt(1_000_000));
        var customer = Customer.register(CustomerId.newId(), phone, otp);
        customers.insert(customer);
        log.info("OTP for {}: {}", phone.value(), otp);
        return customer;
    }

    static Phone phone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw WalletProblem.invalid("phone", "phone is required");
        }
        try {
            return new Phone(phoneNumber);
        } catch (IllegalArgumentException e) {
            throw WalletProblem.invalid("phone", e.getMessage());
        }
    }
}
