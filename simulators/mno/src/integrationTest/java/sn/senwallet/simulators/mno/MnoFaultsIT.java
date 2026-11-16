package sn.senwallet.simulators.mno;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
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
class MnoFaultsIT {

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
        registry.add("simulator.callback-path", () -> "/webhooks/mno");
        registry.add("simulator.hmac-secret", () -> HMAC_SECRET);
        registry.add("simulator.provider", () -> "orange");
        registry.add("simulator.initial-float", () -> "1000000");
    }

    @BeforeEach
    void reset() throws Exception {
        // Async callbacks from the previous test may land after it returns;
        // wait for a quiet window so the next assertion sees only its own.
        long quietUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
        while (System.nanoTime() < quietUntil) {
            RecordedRequest next = CALLBACKS.takeRequest(50, TimeUnit.MILLISECONDS);
            if (next != null) {
                quietUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
            }
        }
        post("/control/float", "{\"available\":1000000}");
    }

    @Test
    void rejectReturns422() throws Exception {
        enqueueFault("disbursements", "reject", 0, 1);
        HttpResponse<String> response = post(
                "/disbursements",
                money("+221770000001", 1_000));
        assertThat(response.statusCode()).isEqualTo(422);
    }

    @Test
    void latencyDelaysResponse() throws Exception {
        enqueueFault("disbursements", "latency", 200, 1);
        Instant start = Instant.now();
        HttpResponse<String> response = post(
                "/disbursements",
                money("+221770000002", 1_000));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(Duration.between(start, Instant.now()).toMillis())
                .isGreaterThanOrEqualTo(180);
        takeCallbacks(1);
    }

    @Test
    void timeoutThenSuccessSendsSucceededCallback() throws Exception {
        enqueueFault("disbursements", "timeout_then_success", 50, 1);
        HttpResponse<String> response = post(
                "/disbursements",
                money("+221770000003", 1_000));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(response.body()).get("status").asString())
                .isEqualTo("pending");
        String id = JSON.readTree(response.body()).get("id").asString();
        CapturedCallback callback = takeCallbacks(1).getFirst();
        assertThat(JSON.readTree(callback.body()).get("status").asString())
                .isEqualTo("succeeded");
        assertThat(JSON.readTree(get("/transactions/" + id).body())
                        .get("status")
                        .asString())
                .isEqualTo("succeeded");
    }

    @Test
    void timeoutThenFailureSendsFailedCallback() throws Exception {
        enqueueFault("disbursements", "timeout_then_failure", 50, 1);
        HttpResponse<String> response = post(
                "/disbursements",
                money("+221770000004", 1_000));
        assertThat(response.statusCode()).isEqualTo(201);
        String id = JSON.readTree(response.body()).get("id").asString();
        CapturedCallback callback = takeCallbacks(1).getFirst();
        assertThat(JSON.readTree(callback.body()).get("status").asString())
                .isEqualTo("failed");
        assertThat(JSON.readTree(get("/transactions/" + id).body())
                        .get("status")
                        .asString())
                .isEqualTo("failed");
    }

    @Test
    void duplicateCallbackDeliversTwice() throws Exception {
        enqueueFault("disbursements", "duplicate_callback", 30, 1);
        assertThat(post("/disbursements", money("+221770000005", 1_000)).statusCode())
                .isEqualTo(201);
        List<CapturedCallback> received = takeCallbacks(2);
        assertThat(received.get(0).body()).isEqualTo(received.get(1).body());
    }

    @Test
    void outOfOrderCallbackSendsFailureFirst() throws Exception {
        enqueueFault("disbursements", "out_of_order_callback", 40, 1);
        assertThat(post("/disbursements", money("+221770000006", 1_000)).statusCode())
                .isEqualTo(201);
        List<CapturedCallback> received = takeCallbacks(2);
        assertThat(JSON.readTree(received.get(0).body()).get("status").asString())
                .isEqualTo("failed");
        assertThat(JSON.readTree(received.get(1).body()).get("status").asString())
                .isEqualTo("succeeded");
    }

    @Test
    void floatExhaustedLeavesFloatUnchanged() throws Exception {
        long before = JSON.readTree(get("/float").body()).get("available").asLong();
        enqueueFault("disbursements", "float_exhausted", 0, 1);
        HttpResponse<String> response = post(
                "/disbursements",
                money("+221770000007", 1_000));
        assertThat(response.statusCode()).isEqualTo(422);
        long after = JSON.readTree(get("/float").body()).get("available").asLong();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void collectionHappyPathCreditsFloat() throws Exception {
        long before = JSON.readTree(get("/float").body()).get("available").asLong();
        HttpResponse<String> response = post(
                "/collections",
                money("+221770000008", 5_000));
        assertThat(response.statusCode()).isEqualTo(201);
        long after = JSON.readTree(get("/float").body()).get("available").asLong();
        assertThat(after).isEqualTo(before + 5_000);
        takeCallbacks(1);
    }

    private static MockWebServer startCallbacks() {
        try {
            MockWebServer server = new MockWebServer();
            // Always-200 so leftover async callbacks never block waiting for
            // an enqueued MockResponse (flake under CI load).
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse().setResponseCode(200);
                }
            });
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

    private List<CapturedCallback> takeCallbacks(int expected) throws InterruptedException {
        HmacSigner signer = new HmacSigner(HMAC_SECRET);
        List<CapturedCallback> received = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (received.size() < expected && System.nanoTime() < deadline) {
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
            received.add(new CapturedCallback(body));
        }
        assertThat(received).hasSize(expected);
        return received;
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

    private static String money(String phone, long amount) {
        return """
                {"phone":"%s","amount":%d,"currency":"XOF"}
                """.formatted(phone, amount);
    }

    private record CapturedCallback(String body) {
    }
}
