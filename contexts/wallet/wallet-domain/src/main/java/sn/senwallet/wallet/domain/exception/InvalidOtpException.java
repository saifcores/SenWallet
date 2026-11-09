package sn.senwallet.wallet.domain.exception;

/** The code presented at verification does not match the one that was issued. */
public class InvalidOtpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidOtpException() {
        super("otp does not match");
    }
}
