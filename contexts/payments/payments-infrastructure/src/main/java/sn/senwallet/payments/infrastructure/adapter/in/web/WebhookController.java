package sn.senwallet.payments.infrastructure.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import sn.senwallet.payments.application.exception.PaymentProblem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores a signed callback and answers 200 before any saga step. Duplicate
 * nonces are also 200. The worker applies the inbox.
 */
@RestController
public class WebhookController {

    static final String TIMESTAMP = "X-Senwallet-Timestamp";
    static final String NONCE = "X-Senwallet-Nonce";
    static final String SIGNATURE = "X-Senwallet-Signature";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock clock;
    private final byte[] secret;
    private final Duration maxAge;

    public WebhookController(
            JdbcClient jdbc,
            Clock clock,
            @Value("${payments.webhooks.hmac-secret:sim-hmac-secret}") String secret,
            @Value("${payments.webhooks.max-age:5m}") Duration maxAge) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.maxAge = maxAge;
    }

    @PostMapping(path = "/webhooks/mno", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void mno(
            @RequestHeader(TIMESTAMP) String timestamp,
            @RequestHeader(NONCE) String nonce,
            @RequestHeader(SIGNATURE) String signature,
            @RequestBody String body) {
        accept("mno", timestamp, nonce, signature, body, true);
    }

    @PostMapping(path = "/webhooks/pispi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void pispi(
            @RequestHeader(TIMESTAMP) String timestamp,
            @RequestHeader(NONCE) String nonce,
            @RequestHeader(SIGNATURE) String signature,
            @RequestBody String body) {
        accept("pispi", timestamp, nonce, signature, body, false);
    }

    private void accept(String provider, String timestamp, String nonce, String signature, String body, boolean mno) {
        verify(timestamp, nonce, body, signature);
        JsonNode tree;
        try {
            tree = JSON.readTree(body);
        } catch (RuntimeException e) {
            throw PaymentProblem.invalid("body", "callback body must be json");
        }
        var status = text(tree, "status");
        var providerRef = text(tree, "id");
        var externalRef = mno ? text(tree, "external_id") : text(tree, "end_to_end_id");
        var storedProvider = mno ? text(tree, "provider") : provider;
        if (storedProvider == null) {
            storedProvider = provider;
        }
        try {
            jdbc.sql("""
                    INSERT INTO callback_inbox (
                        provider, nonce, provider_ref, external_ref, status, body, received_at)
                    VALUES (:provider, :nonce, :providerRef, :externalRef, :status, CAST(:body AS jsonb), now())
                    """)
                    .param("provider", storedProvider)
                    .param("nonce", nonce)
                    .param("providerRef", blankToNull(providerRef))
                    .param("externalRef", blankToNull(externalRef))
                    .param("status", status)
                    .param("body", body)
                    .update();
        } catch (DuplicateKeyException ignored) {
            // The same nonce was already stored.
        }
    }

    private void verify(String timestamp, String nonce, String body, String signature) {
        if (timestamp == null || nonce == null || nonce.isBlank() || signature == null) {
            throw new PaymentProblem(401, "invalid_signature", "callback signature is missing");
        }
        long millis;
        try {
            millis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new PaymentProblem(401, "invalid_signature", "callback timestamp is invalid");
        }
        var age = Duration.between(java.time.Instant.ofEpochMilli(millis), clock.instant()).abs();
        if (age.compareTo(maxAge) > 0) {
            throw new PaymentProblem(401, "invalid_signature", "callback timestamp is outside the window");
        }
        var expected = sign(timestamp, nonce, body);
        if (!java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new PaymentProblem(401, "invalid_signature", "callback signature does not match");
        }
    }

    private String sign(String timestamp, String nonce, String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var digest = mac.doFinal((timestamp + "\n" + nonce + "\n" + body).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(JsonNode tree, String field) {
        var node = tree.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        var value = node.asString();
        return value.isBlank() ? null : value;
    }
}
