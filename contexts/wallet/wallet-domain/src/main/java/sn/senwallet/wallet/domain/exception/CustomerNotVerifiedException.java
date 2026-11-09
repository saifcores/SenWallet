package sn.senwallet.wallet.domain.exception;

/** A KYC change was asked of a customer who has not verified the phone. */
public class CustomerNotVerifiedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CustomerNotVerifiedException() {
        super("customer is not verified");
    }
}
