package sn.senwallet.wallet.domain.model;

import java.util.Objects;
import sn.senwallet.shared.id.CustomerId;
import sn.senwallet.wallet.domain.exception.CustomerAlreadyVerifiedException;
import sn.senwallet.wallet.domain.exception.CustomerNotVerifiedException;
import sn.senwallet.wallet.domain.exception.InvalidOtpException;

/**
 * A person known by phone. Unverified customers hold the simulated OTP;
 * verification sets tier N1 and clears the code.
 */
public record Customer(
        CustomerId id,
        Phone phone,
        CustomerStatus status,
        KycTier tier,
        String otp) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(status, "status");
        if (status == CustomerStatus.VERIFIED) {
            Objects.requireNonNull(tier, "tier");
            if (otp != null) {
                throw new IllegalArgumentException("a verified customer has no otp");
            }
        } else if (tier != null) {
            throw new IllegalArgumentException("an unverified customer has no tier");
        } else if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("otp must be 6 digits");
        }
    }

    public static Customer register(CustomerId id, Phone phone, String otp) {
        return new Customer(id, phone, CustomerStatus.UNVERIFIED, null, otp);
    }

    public Customer verify(String code) {
        if (status == CustomerStatus.VERIFIED) {
            throw new CustomerAlreadyVerifiedException();
        }
        if (!otp.equals(code)) {
            throw new InvalidOtpException();
        }
        return new Customer(id, phone, CustomerStatus.VERIFIED, KycTier.N1, null);
    }

    public Customer withTier(KycTier next) {
        if (status != CustomerStatus.VERIFIED) {
            throw new CustomerNotVerifiedException();
        }
        Objects.requireNonNull(next, "next");
        return new Customer(id, phone, CustomerStatus.VERIFIED, next, null);
    }
}
