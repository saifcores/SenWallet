package sn.senwallet.payments.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(String... parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var canonical = String.join("|", parts);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
