package sn.senwallet.wallet.domain.model;

import java.util.Objects;

/** E.164 telephone number, also used as the local PI-SPI alias. */
public record Phone(String value) {

    public Phone {
        Objects.requireNonNull(value, "value");
        if (!value.matches("\\+[1-9]\\d{7,14}")) {
            throw new IllegalArgumentException("phone must be E.164");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
