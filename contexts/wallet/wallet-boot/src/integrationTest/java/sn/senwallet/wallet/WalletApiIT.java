package sn.senwallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import sn.senwallet.ledger.LedgerApplication;
import sn.senwallet.testsupport.SenwalletKeycloak;
import sn.senwallet.testsupport.SenwalletPostgres;
import sn.senwallet.testsupport.SenwalletRabbit;
import sn.senwallet.wallet.infrastructure.adapter.out.notify.LoggingSms;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises 4–6 through the wallet resource server, against the real ledger.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletApiIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicInteger PHONES = new AtomicInteger(77_000_0100);
    private static final int LEDGER_PORT = startLedger();
    private static final ListAppender<ILoggingEvent> SMS = attachSms();

    @LocalServerPort
    int port;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @DynamicPropertySource
    static void wallet(DynamicPropertyRegistry registry) {
        SenwalletPostgres.walletProperties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.flyway.locations", () -> "classpath:db/wallet");
        registry.add("spring.rabbitmq.host", SenwalletRabbit::host);
        registry.add("spring.rabbitmq.port", () -> Integer.toString(SenwalletRabbit.port()));
        registry.add("spring.rabbitmq.username", () -> SenwalletRabbit.USER);
        registry.add("spring.rabbitmq.password", () -> SenwalletRabbit.PASSWORD);
        registry.add("wallet.ledger.base-url", () -> "http://localhost:" + LEDGER_PORT);
        SenwalletKeycloak.walletProperties().forEach((key, value) -> registry.add(key, () -> value));
        registry.add("spring.jmx.enabled", () -> "false");
    }

    @BeforeAll
    static void containersAreUp() {
        assertThat(LEDGER_PORT).isPositive();
    }

    @Test
    void specAndControllersDescribeTheSameOperations() {
        var options = new ParseOptions();
        options.setResolve(true);
        var location = Path.of(System.getProperty("senwallet.root"), "contracts", "openapi", "wallet.yaml");
        var result = new OpenAPIV3Parser().readLocation(location.toUri().toString(), null, options);
        assertThat(result.getMessages()).as("spec validation messages").isEmpty();
        OpenAPI spec = result.getOpenAPI();

        var documented = new TreeSet<String>();
        spec.getPaths().forEach((path, item) -> item.readOperationsMap().keySet()
                .forEach(verb -> documented.add(verb.name().toUpperCase(Locale.ROOT) + " " + path)));

        var served = new TreeSet<String>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("sn.senwallet.wallet")) {
                return;
            }
            for (var path : info.getPatternValues()) {
                for (var verb : info.getMethodsCondition().getMethods()) {
                    served.add(verb.name() + " " + path);
                }
            }
        });
        assertThat(served).isEqualTo(documented);
    }

    @Test
    void creditAboveTheCapIsRejectedAndWritesNothing() throws Exception {
        var sender = verifiedCustomer();
        var recipient = verifiedCustomer();
        fund(sender, 1_000);
        fund(recipient, 4_000_000);

        var refused = transfer(sender.walletId(), recipient.phone(), 1);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body().path("code").asString()).isEqualTo("balance_condition_violated");
        var transferId = refused.body().path("transfer_id").asString();
        assertThat(ledgerTransactions("p2p:" + transferId)).isZero();
        assertThat(wallet(recipient.walletId()).path("balances").path("available").asLong()).isEqualTo(4_000_000);
    }

    @Test
    void internalSendCashPostsTheFeeAndNotifiesOnce() throws Exception {
        var adama = verifiedCustomer();
        var binta = verifiedCustomer();
        fund(adama, 100_000);
        SMS.list.clear();

        var posted = transfer(adama.walletId(), binta.phone(), 50_000);
        assertThat(posted.status()).isEqualTo(201);
        var replay = transfer(adama.walletId(), binta.phone(), 50_000, posted.key());
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.raw()).isEqualTo(posted.raw());
        assertThat(ledgerTransactions("p2p:" + posted.body().path("id").asString())).isEqualTo(1);

        var conflict = transfer(adama.walletId(), binta.phone(), 1, posted.key());
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().path("code").asString()).isEqualTo("idempotency_conflict");

        assertThat(wallet(adama.walletId()).path("balances").path("available").asLong()).isEqualTo(49_500);
        assertThat(wallet(binta.walletId()).path("balances").path("available").asLong()).isEqualTo(50_000);
        assertThat(ledgerAccount(feeAccountId()).path("balances").path("posted").asLong()).isEqualTo(500);

        var externalId = "p2p:" + posted.body().path("id").asString();
        awaitHistory(adama.walletId(), externalId);
        awaitHistory(binta.walletId(), externalId);
        awaitSms(externalId, 2);

        var beforeSms = smsCount(externalId);
        rabbit.send("ledger", "ledger.transaction.posted", MessageBuilder
                .withBody(outboxPayload(externalId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build());
        Thread.sleep(Duration.ofSeconds(2));
        assertThat(history(adama.walletId(), externalId)).hasSize(1);
        assertThat(history(binta.walletId(), externalId)).hasSize(1);
        assertThat(smsCount(externalId)).isEqualTo(beforeSms);
        assertThat(smsText(externalId)).anyMatch(text -> text.contains("Vous avez envoyé 50000 XOF"));
        assertThat(smsText(externalId)).anyMatch(text -> text.contains("Vous avez reçu 50000 XOF"));
    }

    @Test
    void registerDoesNotNeedAToken() throws Exception {
        var phone = "+221" + PHONES.getAndIncrement();
        var registered = postAnonymous("/customers", "{\"phone\":\"" + phone + "\"}");
        assertThat(registered.status()).isEqualTo(201);
    }

    @Test
    void transferWithoutATokenIs401() throws Exception {
        var sender = verifiedCustomer();
        var recipient = verifiedCustomer();
        fund(sender, 1_000);
        var rejected = postAnonymous("/transfers", """
                {"from_wallet_id":"%s","to_phone":"%s","amount":1,"currency":"XOF"}
                """.formatted(sender.walletId(), recipient.phone()));
        assertThat(rejected.status()).isEqualTo(401);
    }

    /**
     * Exercise 5: 100 concurrent P2P of 20 000 XOF (fee 200) from a wallet
     * holding 1 010 000. Exactly 50 succeed, available is 0.
     */
    @Test
    void concurrentP2pNeverOverdraws() throws Exception {
        var sender = verifiedCustomer();
        var recipient = verifiedCustomer();
        fund(sender, 1_010_000);

        var start = new CountDownLatch(1);
        var calls = new ArrayList<Future<Reply>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    return transfer(sender.walletId(), recipient.phone(), 20_000);
                }));
            }
            start.countDown();
        }
        var statuses = new ArrayList<Integer>();
        for (var call : calls) {
            statuses.add(call.get().status());
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(50);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(50);
        assertThat(wallet(sender.walletId()).path("balances").path("available").asLong())
                .isEqualTo(0);
        assertThat(wallet(sender.walletId()).path("balances").path("posted").asLong())
                .isGreaterThanOrEqualTo(0);
    }

    private Opened verifiedCustomer() throws Exception {
        var phone = "+221" + PHONES.getAndIncrement();
        var registered = post("/customers", "{\"phone\":\"" + phone + "\"}", null);
        assertThat(registered.status()).isEqualTo(201);
        var id = registered.body().path("id").asString();
        var otp = registered.body().path("otp").asString();
        var verified = post("/customers/" + id + "/verification", "{\"otp\":\"" + otp + "\"}", null);
        assertThat(verified.status()).as(verified.raw()).isEqualTo(201);
        return new Opened(phone, verified.body().path("wallet_id").asString(),
                verified.body().path("ledger_account_id").asString(),
                wallet(verified.body().path("wallet_id").asString()).path("ledger_id").asString());
    }

    private void fund(Opened wallet, long amount) throws Exception {
        var settlement = ledgerPost("/ledger_accounts", """
                {"ledger_id":"%s","name":"Compensation %s","normal_balance":"debit"}
                """.formatted(wallet.ledgerId(), wallet.walletId()));
        assertThat(settlement.status()).as(settlement.raw()).isEqualTo(201);
        var funded = ledgerPost("/ledger_transactions", """
                {"ledger_id":"%s","external_id":"pispi-in:%s","status":"posted","entries":[
                  {"account_id":"%s","direction":"debit","amount":%d},
                  {"account_id":"%s","direction":"credit","amount":%d}
                ]}
                """.formatted(wallet.ledgerId(), wallet.walletId(), settlement.body().path("id").asString(), amount,
                wallet.accountId(), amount));
        assertThat(funded.status()).as(funded.raw()).isEqualTo(201);
    }

    private Reply transfer(String fromWalletId, String toPhone, long amount) throws Exception {
        return transfer(fromWalletId, toPhone, amount, UUID.randomUUID().toString());
    }

    private Reply transfer(String fromWalletId, String toPhone, long amount, String key) throws Exception {
        return post("/transfers", """
                {"from_wallet_id":"%s","to_phone":"%s","amount":%d,"currency":"XOF"}
                """.formatted(fromWalletId, toPhone, amount), key);
    }

    private JsonNode wallet(String id) throws Exception {
        var response = get("/wallets/" + id);
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response.body();
    }

    private List<JsonNode> history(String walletId, String externalId) throws Exception {
        var response = get("/wallets/" + walletId + "/history");
        assertThat(response.status()).isEqualTo(200);
        var matches = new ArrayList<JsonNode>();
        var entries = response.body().path("entries").iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (externalId.equals(entry.path("external_id").asString())) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private void awaitHistory(String walletId, String externalId) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (history(walletId, externalId).size() == 1) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(history(walletId, externalId)).as("history for " + externalId).hasSize(1);
    }

    private void awaitSms(String externalId, int count) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (smsCount(externalId) >= count) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(smsCount(externalId)).isGreaterThanOrEqualTo(count);
    }

    private static int smsCount(String externalId) {
        return smsText(externalId).size();
    }

    private static List<String> smsText(String externalId) {
        return SMS.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(externalId))
                .toList();
    }

    private Reply post(String path, String body, String idempotencyKey) throws Exception {
        return post(path, body, idempotencyKey, SenwalletKeycloak.customerToken());
    }

    private Reply postAnonymous(String path, String body) throws Exception {
        return post(path, body, UUID.randomUUID().toString(), null);
    }

    private Reply post(String path, String body, String idempotencyKey, String bearer)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        request.POST(HttpRequest.BodyPublishers.ofString(body));
        return send(request.build(), idempotencyKey);
    }

    private Reply get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + SenwalletKeycloak.customerToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(), null);
    }

    private static Reply ledgerPost(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + LEDGER_PORT + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + SenwalletKeycloak.serviceToken())
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), null);
    }

    private static JsonNode ledgerAccount(String id) throws Exception {
        var response = send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + LEDGER_PORT + "/ledger_accounts/" + id))
                .header("Authorization", "Bearer " + SenwalletKeycloak.serviceToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(), null);
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response.body();
    }

    private static Reply send(HttpRequest request, String key) throws Exception {
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        var raw = response.body() == null ? "" : response.body();
        var body = raw.isBlank() ? JSON.missingNode() : JSON.readTree(raw);
        return new Reply(response.statusCode(), body, raw, key);
    }

    private static int ledgerTransactions(String externalId) throws Exception {
        try (var connection = DriverManager.getConnection(
                SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_APP,
                SenwalletPostgres.LEDGER_APP_PASSWORD);
                var statement = connection.prepareStatement(
                        "select count(*) from ledger_transactions where external_id = ?")) {
            statement.setString(1, externalId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static String feeAccountId() throws Exception {
        try (var connection = DriverManager.getConnection(
                SenwalletPostgres.jdbcUrl(SenwalletPostgres.WALLET_DATABASE),
                SenwalletPostgres.WALLET_APP,
                SenwalletPostgres.WALLET_APP_PASSWORD);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("select fee_account_id from platform_accounts")) {
            assertThat(rows.next()).isTrue();
            return rows.getObject(1).toString();
        }
    }

    private static String outboxPayload(String externalId) throws Exception {
        try (var connection = DriverManager.getConnection(
                SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_APP,
                SenwalletPostgres.LEDGER_APP_PASSWORD);
                var statement = connection.prepareStatement(
                        "select payload::text from outbox where payload::text like ?")) {
            statement.setString(1, "%" + externalId + "%");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("outbox row for " + externalId).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static int startLedger() {
        var properties = new HashMap<String, Object>();
        SenwalletPostgres.ledgerProperties().forEach(properties::put);
        properties.put("spring.flyway.locations", "classpath:db/migration");
        properties.put("spring.rabbitmq.host", SenwalletRabbit.host());
        properties.put("spring.rabbitmq.port", Integer.toString(SenwalletRabbit.port()));
        properties.put("spring.rabbitmq.username", SenwalletRabbit.USER);
        properties.put("spring.rabbitmq.password", SenwalletRabbit.PASSWORD);
        properties.put("server.port", "0");
        properties.put("spring.jmx.enabled", "false");
        properties.put("ledger.outbox.relay-enabled", "true");
        properties.putAll(SenwalletKeycloak.ledgerProperties());
        ConfigurableApplicationContext context = new SpringApplicationBuilder(LedgerApplication.class)
                .initializers(applicationContext -> applicationContext.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("ledger-it", properties)))
                .run();
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    private static ListAppender<ILoggingEvent> attachSms() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(LoggingSms.LOGGER_NAME)).addAppender(appender);
        return appender;
    }

    private record Opened(String phone, String walletId, String accountId, String ledgerId) {
    }

    private record Reply(int status, JsonNode body, String raw, String key) {
    }
}
