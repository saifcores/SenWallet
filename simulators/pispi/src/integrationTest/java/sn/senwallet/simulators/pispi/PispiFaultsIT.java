package sn.senwallet.simulators.pispi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sn.senwallet.simulators.common.hmac.HmacSigner;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PispiFaultsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String HMAC_SECRET = "it-hmac-secret";
    private static final MockWebServer CALLBACKS = startCallbacks();

    @LocalServerPort
    int port;

    @AfterAll
    static void stopSink() throws IOException {
        CALLBACKS.shutdown();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add(
                "simulator.callback-base-url",
                () -> CALLBACKS.url("/").toString().replaceAll("/$", ""));
        registry.add("simulator.callback-path", () -> "/webhooks/pispi");
        registry.add("simulator.hmac-secret", () -> HMAC_SECRET);
    }

    @BeforeEach
    void drain() throws InterruptedException {
        while (CALLBACKS.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain
        }
    }

    @Test
    void rejectReturns422() throws Exception {
        enqueueFault("transfers", "reject", 0, 1);
        assertThat(post("/transfers", transfer("a", "b", 1_000)).statusCode())
                .isEqualTo(422);
    }

    @Test
    void latencyDelaysResponse() throws Exception {
        enqueueFault("transfers", "latency", 200, 1);
        Instant start = Instant.now();
        assertThat(post("/transfers", transfer("a", "b", 1_000)).statusCode())
                .isEqualTo(201);
        assertThat(Duration.between(start, Instant.now()).toMillis())
                .isGreaterThanOrEqualTo(180);
    }

    @Test
    void timeoutThenSuccessCallback() throws Exception {
        enqueueFault("transfers", "timeout_then_success", 40, 1);
        CALLBACKS.enqueue(new MockResponse().setResponseCode(200));
        HttpResponse<String> response = post("/transfers", transfer("a", "b", 2_000));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(response.body()).get("status").asString())
                .isEqualTo("pending");
        String id = JSON.readTree(response.body()).get("id").asString();
        assertThat(JSON.readTree(takeCallbacks(1).getFirst()).get("status").asString())
                .isEqualTo("posted");
        assertThat(JSON.readTree(get("/transfers/" + id).body())
                        .get("status")
                        .asString())
                .isEqualTo("posted");
    }

    @Test
    void timeoutThenFailureUpdatesStatusAndCallback() throws Exception {
        enqueueFault("transfers", "timeout_then_failure", 40, 1);
        CALLBACKS.enqueue(new MockResponse().setResponseCode(200));
        HttpResponse<String> response = post("/transfers", transfer("a", "b", 3_000));
        assertThat(response.statusCode()).isEqualTo(201);
        String id = JSON.readTree(response.body()).get("id").asString();
        assertThat(JSON.readTree(takeCallbacks(1).getFirst()).get("status").asString())
                .isEqualTo("failed");
        assertThat(JSON.readTree(get("/transfers/" + id).body())
                        .get("status")
                        .asString())
                .isEqualTo("failed");
    }

    @Test
    void inboundFiresSignedWebhookAndSettlementListsIt() throws Exception {
        CALLBACKS.enqueue(new MockResponse().setResponseCode(200));
        HttpResponse<String> response = post(
                "/control/inbound",
                "{\"end_to_end_id\":\"e2e-in-1\",\"debtor_alias\":\"+221770000010\","
                        + "\"creditor_alias\":\"+221770000011\",\"amount\":100000,"
                        + "\"currency\":\"XOF\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        String callback = takeCallbacks(1).getFirst();
        assertThat(JSON.readTree(callback).get("end_to_end_id").asString())
                .isEqualTo("e2e-in-1");
        HttpResponse<String> report = get(
                "/reports/settlement?date=" + LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(report.body()).get("lines").isArray()).isTrue();
        assertThat(JSON.readTree(report.body()).get("lines").size()).isGreaterThanOrEqualTo(1);
    }

    private static MockWebServer startCallbacks() {
        try {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void enqueueFault(String operation, String mode, long delayMs, int count)
            throws Exception {
        String payload = """
                {"operation":"%s","mode":"%s","probability":1.0,"delay_ms":%d,"count":%d}
                """.formatted(operation, mode, delayMs, count);
        assertThat(post("/control/faults", payload).statusCode()).isEqualTo(201);
    }

    private List<String> takeCallbacks(int expected) throws InterruptedException {
        HmacSigner signer = new HmacSigner(HMAC_SECRET);
        List<String> bodies = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (bodies.size() < expected && System.nanoTime() < deadline) {
            RecordedRequest next = CALLBACKS.takeRequest(50, TimeUnit.MILLISECONDS);
            if (next == null) {
                continue;
            }
            String body = next.getBody().readUtf8();
            assertThat(signer.verify(
                    next.getHeader(HmacSigner.HEADER_TIMESTAMP),
                    next.getHeader(HmacSigner.HEADER_NONCE),
                    body,
                    next.getHeader(HmacSigner.HEADER_SIGNATURE))).isTrue();
            bodies.add(body);
        }
        assertThat(bodies).hasSize(expected);
        return bodies;
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static String transfer(String debtor, String creditor, long amount) {
        return """
                {"debtor_alias":"%s","creditor_alias":"%s","amount":%d,"currency":"XOF"}
                """.formatted(debtor, creditor, amount);
    }
}
