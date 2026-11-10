package sn.senwallet.testsupport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * One Keycloak 26 container per test JVM, importing the same realm as Compose.
 * A free host port is pinned before start so the issuer URL matches the
 * {@code iss} claim and is reachable from the test JVM.
 */
public final class SenwalletKeycloak {

    public static final String REALM = "senwallet";
    public static final String CUSTOMER_USER = "adama";
    public static final String CUSTOMER_PASSWORD = "password";
    public static final String SERVICE_CLIENT = "wallet-service";
    public static final String SERVICE_SECRET = "wallet-service-secret";
    public static final String WEB_CLIENT = "wallet-web";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final int PORT = freePort();
    private static final String PUBLIC_URL = "http://127.0.0.1:" + PORT;
    private static final GenericContainer<?> CONTAINER = start();
    private static final AtomicReference<CachedToken> SERVICE = new AtomicReference<>();
    private static final AtomicReference<CachedToken> CUSTOMER = new AtomicReference<>();

    private SenwalletKeycloak() {
    }

    /** Issuer of the imported {@code senwallet} realm. */
    public static String issuerUri() {
        return PUBLIC_URL + "/realms/" + REALM;
    }

    /** Spring properties for the ledger resource server. */
    public static Map<String, String> ledgerProperties() {
        var properties = new java.util.LinkedHashMap<String, String>();
        properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                issuerUri());
        properties.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                issuerUri() + "/protocol/openid-connect/certs");
        properties.put("spring.security.oauth2.resourceserver.jwt.audiences", "ledger");
        properties.putAll(clientProperties());
        return properties;
    }

    /** Spring properties for the wallet resource server and service client. */
    public static Map<String, String> walletProperties() {
        var properties = new java.util.LinkedHashMap<String, String>();
        properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                issuerUri());
        properties.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                issuerUri() + "/protocol/openid-connect/certs");
        properties.put("spring.security.oauth2.resourceserver.jwt.audiences", "wallet");
        properties.putAll(clientProperties());
        return properties;
    }

    /**
     * Client-credentials registration. Needed when the wallet classpath (oauth2
     * client) is used to boot the ledger in {@code WalletApiIT}.
     */
    public static Map<String, String> clientProperties() {
        return Map.of(
                "spring.security.oauth2.client.provider.keycloak.token-uri",
                issuerUri() + "/protocol/openid-connect/token",
                "spring.security.oauth2.client.registration.wallet-service.client-id",
                SERVICE_CLIENT,
                "spring.security.oauth2.client.registration.wallet-service.client-secret",
                SERVICE_SECRET,
                "spring.security.oauth2.client.registration.wallet-service.authorization-grant-type",
                "client_credentials",
                "spring.security.oauth2.client.registration.wallet-service.provider",
                "keycloak");
    }

    /**
     * Client-credentials token for {@code wallet-service} (audience
     * {@code ledger}).
     */
    public static String serviceToken() {
        return token(SERVICE, () -> form(
                "grant_type", "client_credentials",
                "client_id", SERVICE_CLIENT,
                "client_secret", SERVICE_SECRET));
    }

    /**
     * Password-grant token for Adama (role {@code customer}, audience
     * {@code wallet}).
     */
    public static String customerToken() {
        return token(CUSTOMER, () -> form(
                "grant_type", "password",
                "client_id", WEB_CLIENT,
                "username", CUSTOMER_USER,
                "password", CUSTOMER_PASSWORD));
    }

    private static String token(AtomicReference<CachedToken> slot, java.util.function.Supplier<String> body) {
        var cached = slot.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.value();
        }
        var response = postToken(body.get());
        var access = jsonField(response, "access_token");
        var expiresIn = Long.parseLong(jsonField(response, "expires_in"));
        var fresh = new CachedToken(access, Instant.now().plusSeconds(Math.max(expiresIn - 30, 30)));
        slot.set(fresh);
        return fresh.value();
    }

    private static String postToken(String body) {
        var request = HttpRequest.newBuilder(URI.create(issuerUri() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "token endpoint returned " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String form(String... keysAndValues) {
        var body = new StringBuilder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(keysAndValues[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(keysAndValues[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static String jsonField(String json, String name) {
        var key = "\"" + name + "\"";
        var at = json.indexOf(key);
        if (at < 0) {
            throw new IllegalStateException(name + " missing from " + json);
        }
        var colon = json.indexOf(':', at + key.length());
        var rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            return rest.substring(1, rest.indexOf('"', 1));
        }
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
            end++;
        }
        return rest.substring(0, end);
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> start() {
        var container = new GenericContainer<>("quay.io/keycloak/keycloak:26.3")
                .withExposedPorts(8080)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HTTP_ENABLED", "true")
                .withCopyFileToContainer(MountableFile.forHostPath(realmFile(), 0644),
                        "/opt/keycloak/data/import/senwallet-realm.json")
                .withCommand(
                        "start-dev",
                        "--import-realm",
                        "--http-enabled=true",
                        "--hostname-strict=false",
                        "--hostname=" + PUBLIC_URL)
                .waitingFor(Wait.forHttp("/realms/senwallet/.well-known/openid-configuration")
                        .forPort(8080)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        container.setPortBindings(List.of(PORT + ":8080"));
        container.start();
        return container;
    }

    private static Path realmFile() {
        var root = System.getProperty("senwallet.root");
        if (root == null) {
            throw new IllegalStateException(
                    "system property senwallet.root is not set (see senwallet.integration-test)");
        }
        var file = Path.of(root, "deploy", "keycloak", "senwallet-realm.json");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("realm file not found: " + file);
        }
        return file;
    }

    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
