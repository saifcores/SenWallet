package sn.senwallet.ledger.domain.model.transaction;

import java.util.HexFormat;
import java.util.Objects;

/**
 * Fingerprint of the request that created a transaction. A replay with the same
 * external id and the same hash returns the stored transaction; a different
 * hash is a conflict.
 */
public record RequestHash(String hex) {

    public RequestHash {
        Objects.requireNonNull(hex, "hex");
        if (hex.isBlank()) {
            throw new IllegalArgumentException("request hash must not be blank");
        }
    }

    public static RequestHash of(byte[] digest) {
        return new RequestHash(HexFormat.of().formatHex(digest));
    }

    public byte[] bytes() {
        return HexFormat.of().parseHex(hex);
    }
}
