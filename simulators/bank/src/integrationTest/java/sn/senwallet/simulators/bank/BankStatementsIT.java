package sn.senwallet.simulators.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class BankStatementsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String ACCESS_KEY = "s3mock";
    private static final String SECRET_KEY = "s3mock";
    private static final String BUCKET = "statements";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> S3 = new GenericContainer<>(
                    DockerImageName.parse("adobe/s3mock:4.9.1"))
            .withExposedPorts(9090)
            .withEnv("initialBuckets", BUCKET);

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("simulator.minio.endpoint", BankStatementsIT::endpoint);
        registry.add("simulator.minio.access-key", () -> ACCESS_KEY);
        registry.add("simulator.minio.secret-key", () -> SECRET_KEY);
        registry.add("simulator.minio.bucket", () -> BUCKET);
        registry.add("simulator.bank-name", () -> "bank-a");
    }

    @Test
    void statementObjectIsWrittenToMinio() throws Exception {
        String account = "cantonment-a";
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(post("/control/credit", credit(account, "ref-1", 10_000)).statusCode())
                .isEqualTo(201);
        assertThat(post("/control/credit", credit(account, "ref-2", 20_000)).statusCode())
                .isEqualTo(201);

        HttpResponse<String> statement = get(
                "/statements?account=" + account + "&date=" + date);
        assertThat(statement.statusCode()).isEqualTo(200);
        String objectKey = JSON.readTree(statement.body()).get("object_key").asString();
        assertThat(JSON.readTree(statement.body()).get("line_count").asInt()).isEqualTo(2);

        String csv = download(objectKey);
        assertThat(csv).contains("ref-1,10000");
        assertThat(csv).contains("ref-2,20000");
    }

    @Test
    void missingStatementLineOmitsLastCredit() throws Exception {
        String account = "cantonment-missing";
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(post("/control/credit", credit(account, "keep", 5_000)).statusCode())
                .isEqualTo(201);
        assertThat(post("/control/credit", credit(account, "drop", 7_000)).statusCode())
                .isEqualTo(201);
        enqueueFault("statements", "missing_statement_line");

        HttpResponse<String> statement = get(
                "/statements?account=" + account + "&date=" + date);
        assertThat(statement.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(statement.body()).get("line_count").asInt()).isEqualTo(1);
        String csv = download(JSON.readTree(statement.body()).get("object_key").asString());
        assertThat(csv).contains("keep,5000");
        assertThat(csv).doesNotContain("drop,7000");
    }

    @Test
    void wrongAmountAltersLastLine() throws Exception {
        String account = "cantonment-wrong";
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(post("/control/credit", credit(account, "ok", 5_000)).statusCode())
                .isEqualTo(201);
        assertThat(post("/control/credit", credit(account, "bad", 7_000)).statusCode())
                .isEqualTo(201);
        enqueueFault("statements", "wrong_amount");

        HttpResponse<String> statement = get(
                "/statements?account=" + account + "&date=" + date);
        assertThat(statement.statusCode()).isEqualTo(200);
        String csv = download(JSON.readTree(statement.body()).get("object_key").asString());
        assertThat(csv).contains("ok,5000");
        assertThat(csv).contains("bad,7001");
        assertThat(csv).doesNotContain("bad,7000");
    }

    private void enqueueFault(String operation, String mode) throws Exception {
        String payload = """
                {"operation":"%s","mode":"%s","probability":1.0,"delay_ms":0,"count":1}
                """.formatted(operation, mode);
        assertThat(post("/control/faults", payload).statusCode()).isEqualTo(201);
    }

    private String download(String objectKey) {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(objectKey)
                            .build());
            return new String(bytes.asByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static String credit(String account, String reference, long amount) {
        return """
                {"account":"%s","reference":"%s","amount":%d,"currency":"XOF"}
                """.formatted(account, reference, amount);
    }
}
