package sn.senwallet.clients.ledger;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import sn.senwallet.clients.ledger.LedgerDtos.Balances;
import sn.senwallet.clients.ledger.LedgerDtos.CreateLedger;
import sn.senwallet.clients.ledger.LedgerDtos.Line;
import sn.senwallet.clients.ledger.LedgerDtos.OpenAccount;
import sn.senwallet.clients.ledger.LedgerDtos.PostTransaction;
import sn.senwallet.clients.ledger.LedgerDtos.Transaction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client of {@code contracts/openapi/ledger.yaml}. Posts are limited to
 * 2 seconds and retried on transport failures, 401 and 5xx, with the same
 * body. 409 and 422 are not retried. A 401 retries so a fresh service token
 * can be fetched; the supplier must not reuse a dead token.
 */
public final class LedgerClient implements AutoCloseable {

    static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final RestClient http;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public LedgerClient(String baseUrl) {
        this(baseUrl, () -> null);
    }

    public LedgerClient(String baseUrl, Supplier<String> accessToken) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(TIMEOUT);
        requests.setReadTimeout(TIMEOUT);
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requests)
                .requestInterceptor((request, body, execution) -> {
                    var token = accessToken.get();
                    if (token != null && !token.isBlank()) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token);
                    }
                    return execution.execute(request, body);
                })
                .build();
        this.retry = Retry.of("ledger-post", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(20))
                .retryOnException(failure -> unwrap(failure) instanceof LedgerTransportException)
                .failAfterMaxAttempts(true)
                .build());
        this.timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(TIMEOUT)
                .cancelRunningFuture(true)
                .build());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public UUID createLedger(String name, String environment) {
        return id(post("/ledgers", new CreateLedger(name, environment)));
    }

    public UUID openAccount(OpenAccount request) {
        return id(post("/ledger_accounts", request));
    }

    public UUID postTransaction(PostTransaction request) {
        return withRetry(() -> id(post("/ledger_transactions", request)));
    }

    public Transaction getTransaction(UUID id) {
        var tree = get("/ledger_transactions/" + id);
        var entries = new ArrayList<Line>();
        var nodes = tree.path("entries").iterator();
        while (nodes.hasNext()) {
            var entry = nodes.next();
            entries.add(new Line(
                    UUID.fromString(entry.path("account_id").asString()),
                    entry.path("direction").asString(),
                    entry.path("amount").asLong()));
        }
        return new Transaction(UUID.fromString(tree.path("id").asString()), tree.path("external_id").asString(),
                entries);
    }

    public Balances getBalances(UUID accountId) {
        var balances = get("/ledger_accounts/" + accountId).path("balances");
        return new Balances(
                balances.path("posted").asLong(),
                balances.path("pending").asLong(),
                balances.path("available").asLong());
    }

    private <T> T withRetry(Supplier<T> call) {
        try {
            return retry.executeCallable(() -> timeLimiter.executeFutureSupplier(
                    () -> CompletableFuture.supplyAsync(call, executor)));
        } catch (LedgerRejectedException rejected) {
            throw rejected;
        } catch (Exception failure) {
            var root = unwrap(failure);
            if (root instanceof LedgerRejectedException rejected) {
                throw rejected;
            }
            if (root instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new LedgerTransportException(root.getMessage(), root);
        }
    }

    private JsonNode post(String path, Object body) {
        return exchange(() -> http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, LedgerClient::handleError)
                .body(String.class));
    }

    private JsonNode get(String path) {
        return exchange(() -> http.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, LedgerClient::handleError)
                .body(String.class));
    }

    private static JsonNode exchange(Supplier<String> call) {
        try {
            var raw = call.get();
            return raw == null || raw.isBlank() ? JSON.missingNode() : JSON.readTree(raw);
        } catch (LedgerRejectedException | LedgerTransportException known) {
            throw known;
        } catch (RestClientException failure) {
            throw new LedgerTransportException(failure.getMessage(), failure);
        }
    }

    private static void handleError(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        var raw = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var status = response.getStatusCode().value();
        if (status == 401 || response.getStatusCode().is5xxServerError()) {
            throw new LedgerTransportException("ledger responded " + status + ": " + raw);
        }
        throw rejected(status, raw);
    }

    private static LedgerRejectedException rejected(int status, String raw) {
        var code = "unknown";
        var detail = raw;
        try {
            var tree = JSON.readTree(raw);
            if (tree.path("code").isValueNode()) {
                code = tree.path("code").asString();
            }
            if (tree.path("detail").isValueNode()) {
                detail = tree.path("detail").asString();
            }
        } catch (RuntimeException ignored) {
            // The body was not problem+json; the raw text is the detail.
        }
        return new LedgerRejectedException(status, code, detail);
    }

    private static UUID id(JsonNode tree) {
        return UUID.fromString(tree.path("id").asString());
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while (current.getCause() != null && (current instanceof CompletionException
                || current instanceof ExecutionException
                || current instanceof TimeoutException && current.getCause() != null)) {
            current = current.getCause();
        }
        if (current instanceof TimeoutException) {
            return new LedgerTransportException("ledger call timed out", current);
        }
        return current;
    }

    @Override
    public void close() {
        executor.close();
    }
}
