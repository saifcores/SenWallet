package sn.senwallet.shared.id;

import java.util.Objects;
import java.util.UUID;

public record LedgerId(UUID value) {

    public LedgerId {
        Objects.requireNonNull(value, "value");
    }

    public static LedgerId newId() {
        return new LedgerId(UUID.randomUUID());
    }

    public static LedgerId of(String value) {
        return new LedgerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
