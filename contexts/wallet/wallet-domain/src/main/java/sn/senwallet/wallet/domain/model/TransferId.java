package sn.senwallet.wallet.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value");
    }

    public static TransferId newId() {
        return new TransferId(UUID.randomUUID());
    }

    public static TransferId of(UUID value) {
        return new TransferId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
