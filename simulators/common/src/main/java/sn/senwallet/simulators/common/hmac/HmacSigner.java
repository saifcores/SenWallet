package sn.senwallet.simulators.common.hmac;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 over {@code timestamp + "\n" + nonce + "\n" + body}. Headers:
 * {@code X-Senwallet-Timestamp}, {@code X-Senwallet-Nonce},
 * {@code X-Senwallet-Signature}.
 */
public final class HmacSigner {

    public static final String HEADER_TIMESTAMP = "X-Senwallet-Timestamp";
    public static final String HEADER_NONCE = "X-Senwallet-Nonce";
    public static final String HEADER_SIGNATURE = "X-Senwallet-Signature";

    private final byte[] secret;

    public HmacSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("hmac secret is required");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public SignedPayload sign(String body) {
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String signature = signature(timestamp, nonce, body);
        return new SignedPayload(timestamp, nonce, signature, body);
    }

    public boolean verify(String timestamp, String nonce, String body, String signature) {
        String expected = signature(timestamp, nonce, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(String timestamp, String nonce, String body) {
        String payload = timestamp + "\n" + nonce + "\n" + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public record SignedPayload(
            String timestamp,
            String nonce,
            String signature,
            String body) {
    }
}
