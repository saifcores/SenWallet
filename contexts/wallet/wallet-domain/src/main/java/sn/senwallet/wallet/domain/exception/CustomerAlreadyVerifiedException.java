package sn.senwallet.wallet.domain.exception;

/** Verification was asked of a customer who already has a wallet. */
public class CustomerAlreadyVerifiedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CustomerAlreadyVerifiedException() {
        super("customer is already verified");
    }
}
