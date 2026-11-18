package sn.senwallet.payments;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import sn.senwallet.ledger.LedgerApplication;
import sn.senwallet.simulators.mno.MnoApplication;
import sn.senwallet.simulators.pispi.PispiApplication;
import sn.senwallet.testsupport.SenwalletKeycloak;
import sn.senwallet.testsupport.SenwalletPostgres;
import sn.senwallet.testsupport.SenwalletRabbit;
import sn.senwallet.wallet.WalletApplication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises 7 and 8, the monthly cap, SendCash and Pay2Day through the
 * payments resource server, the wallet and the M6 simulators.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class PaymentsApiIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicInteger PHONES = new AtomicInteger(77_110_0000);
    private static final Stack STACK = Stack.boot();

    @BeforeAll
    static void containersAreUp() {
        assertThat(STACK.paymentsPort()).isPositive();
    }

    @Test
    void specAndControllersDescribeTheSameOperations() {
        var options = new ParseOptions();
        options.setResolve(true);
        var location = Path.of(System.getProperty("senwallet.root"), "contracts", "openapi", "payments.yaml");
        var result = new OpenAPIV3Parser().readLocation(location.toUri().toString(), null, options);
        assertThat(result.getMessages()).as("spec validation messages").isEmpty();
        OpenAPI spec = result.getOpenAPI();

        var documented = new TreeSet<String>();
        spec.getPaths().forEach((path, item) -> item.readOperationsMap().keySet()
                .forEach(verb -> documented.add(verb.name().toUpperCase(Locale.ROOT) + " " + path)));

        var served = new TreeSet<String>();
        var mappings = STACK.payments().getBean(
                "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        mappings.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("sn.senwallet.payments")) {
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
    void orangeTimeoutThenSuccessStaysPendingUntilTheRailConfirms() throws Exception {
        var wallet = openWallet();
        topup(wallet, "orange", 50_000);
        fault(STACK.orangePort(), "disbursements", "timeout_then_success", 1_500);

        var started = withdraw(wallet, "orange", 50_000);
        assertThat(started.status()).as(started.raw()).isEqualTo(202);
        var id = started.body().path("id").asString();
        var unknown = awaitState(id, "unknown", "completed");
        assertThat(unknown.path("state").asString()).isEqualTo("unknown");
        assertThat(ledgerStatus("cashout:" + id)).isEqualTo("pending");

        var done = awaitState(id, "completed", null);
        assertThat(done.path("state").asString()).isEqualTo("completed");
        assertThat(ledgerStatus("cashout:" + id)).isEqualTo("posted");
        assertThat(ledgerCount("cashout:" + id)).isEqualTo(1);
    }

    @Test
    void duplicateAndOutOfOrderCallbacksPostOnceAndARejectArchives() throws Exception {
        var duplicate = cashoutAfterFault("duplicate_callback", 0);
        awaitState(duplicate, "completed", null);
        assertThat(ledgerStatus("cashout:" + duplicate)).isEqualTo("posted");
        assertThat(ledgerCount("cashout:" + duplicate)).isEqualTo(1);
        Thread.sleep(400);
        assertThat(ledgerCount("cashout:" + duplicate)).isEqualTo(1);

        var reordered = cashoutAfterFault("out_of_order_callback", 30);
        awaitState(reordered, "completed", null);
        Thread.sleep(500);
        var still = getOrder(reordered);
        assertThat(still.path("state").asString()).isEqualTo("completed");
        assertThat(ledgerCount("cashout:" + reordered)).isEqualTo(1);
        assertThat(ledgerStatus("cashout:" + reordered)).isEqualTo("posted");

        var wallet = openWallet();
        topup(wallet, "orange", 20_000);
        fault(STACK.orangePort(), "disbursements", "reject", 0);
        var refused = withdraw(wallet, "orange", 20_000);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        var id = refused.body().path("order_id").asString();
        assertThat(getOrder(id).path("state").asString()).isEqualTo("rejected");
        assertThat(ledgerStatus("cashout:" + id)).isEqualTo("archived");
        assertThat(countPosted("cashout:" + id)).isZero();
    }

    @Test
    void monthlyTopupCapRejectsTheFrancPastFifteenMillion() throws Exception {
        var wallet = openWallet();
        for (var cycle = 0; cycle < 3; cycle++) {
            topup(wallet, "wave", 4_000_000);
            var withdrawn = withdraw(wallet, "wave", 4_000_000);
            awaitState(withdrawn.body().path("id").asString(), "completed", null);
        }
        topup(wallet, "wave", 3_000_000);
        var before = topupCount(wallet.walletId());

        var refused = postPayments("/topups", payment(wallet, "wave", 1), UUID.randomUUID());
        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.body().path("code").asString()).isEqualTo("monthly_cap_exceeded");
        assertThat(topupCount(wallet.walletId())).isEqualTo(before);
        assertThat(walletView(wallet.walletId()).path("balances").path("available").asLong())
                .isEqualTo(3_000_000);
    }

    @Test
    void unknownPispiAliasCreditsSuspense() throws Exception {
        openWallet();
        var before = suspensePosted();
        var endToEnd = "e2e-unknown-" + UUID.randomUUID();
        var inbound = postRaw(STACK.pispiPort(), "/control/inbound", """
                {"end_to_end_id":"%s","debtor_alias":"+221770000099","creditor_alias":"missing-alias","amount":2500,"currency":"XOF"}
                """.formatted(endToEnd));
        assertThat(inbound.status()).as(inbound.raw()).isEqualTo(201);
        awaitSuspense(before + 2_500);
        assertThat(ledgerStatus("pispi-in:" + endToEnd)).isEqualTo("posted");
        assertThat(orderWallet("pispi-in:" + endToEnd)).isNull();
    }

    @Test
    void sendCashThroughTheApisThenRejectsOneExtraFranc() throws Exception {
        var adama = openWallet();
        var binta = openWallet();
        var endToEnd = "e2e-sendcash-" + UUID.randomUUID();
        var inbound = postRaw(STACK.pispiPort(), "/control/inbound", """
                {"end_to_end_id":"%s","debtor_alias":"+221770000001","creditor_alias":"%s","amount":100000,"currency":"XOF"}
                """.formatted(endToEnd, adama.alias()));
        assertThat(inbound.status()).as(inbound.raw()).isEqualTo(201);
        awaitAvailable(adama.walletId(), 100_000);

        var transfer = postWallet("/transfers", """
                {"from_wallet_id":"%s","to_phone":"%s","amount":50000,"currency":"XOF"}
                """.formatted(adama.walletId(), binta.phone()), UUID.randomUUID());
        assertThat(transfer.status()).as(transfer.raw()).isEqualTo(201);
        awaitAvailable(binta.walletId(), 50_000);
        assertThat(walletView(adama.walletId()).path("balances").path("available").asLong()).isEqualTo(49_500);

        var cashout = withdraw(binta, "orange", 50_000);
        assertThat(cashout.status()).as(cashout.raw()).isIn(201, 202);
        awaitState(cashout.body().path("id").asString(), "completed", null);
        awaitAvailable(binta.walletId(), 0);
        assertThat(ledgerStatus("cashout:" + cashout.body().path("id").asString())).isEqualTo("posted");

        var extra = withdraw(binta, "orange", 1);
        assertThat(extra.status()).as(extra.raw()).isEqualTo(422);
        assertThat(extra.body().path("code").asString()).isEqualTo("balance_condition_violated");
        assertThat(ledgerCount("cashout:" + extra.body().path("order_id").asString())).isZero();
    }

    @Test
    void pay2DayPaysOneHundredCouriersAndWithdrawsEachOnceOnWave() throws Exception {
        var payer = openWallet();
        var endToEnd = "e2e-pay2day-" + UUID.randomUUID();
        var inbound = postRaw(STACK.pispiPort(), "/control/inbound", """
                {"end_to_end_id":"%s","debtor_alias":"+221770000002","creditor_alias":"%s","amount":1515000,"currency":"XOF"}
                """.formatted(endToEnd, payer.alias()));
        assertThat(inbound.status()).as(inbound.raw()).isEqualTo(201);
        awaitAvailable(payer.walletId(), 1_515_000);

        for (var courier = 0; courier < 100; courier++) {
            var dest = openWallet();
            var transfer = postWallet("/transfers", """
                    {"from_wallet_id":"%s","to_phone":"%s","amount":15000,"currency":"XOF"}
                    """.formatted(payer.walletId(), dest.phone()), UUID.randomUUID());
            assertThat(transfer.status()).as(transfer.raw()).isEqualTo(201);
            var cashout = withdraw(dest, "wave", 15_000);
            assertThat(cashout.status()).as(cashout.raw()).isIn(201, 202);
            var id = cashout.body().path("id").asString();
            awaitState(id, "completed", null);
            assertThat(ledgerCount("cashout:" + id)).isEqualTo(1);
            assertThat(ledgerStatus("cashout:" + id)).isEqualTo("posted");
        }
        assertThat(walletView(payer.walletId()).path("balances").path("available").asLong()).isZero();
    }

    private static String cashoutAfterFault(String mode, long delayMs) throws Exception {
        var wallet = openWallet();
        topup(wallet, "orange", 10_000);
        fault(STACK.orangePort(), "disbursements", mode, delayMs);
        var started = withdraw(wallet, "orange", 10_000);
        assertThat(started.status()).as(started.raw()).isIn(201, 202);
        return started.body().path("id").asString();
    }

    private static void topup(Opened wallet, String provider, long amount) throws Exception {
        var reply = postPayments("/topups", payment(wallet, provider, amount), UUID.randomUUID());
        assertThat(reply.status()).as(reply.raw()).isIn(201, 202);
        awaitState(reply.body().path("id").asString(), "completed", null);
    }

    private static Reply withdraw(Opened wallet, String provider, long amount) throws Exception {
        return postPayments("/withdrawals", payment(wallet, provider, amount), UUID.randomUUID());
    }

    private static String payment(Opened wallet, String provider, long amount) {
        return """
                {"wallet_id":"%s","provider":"%s","amount":%d,"currency":"XOF"}
                """.formatted(wallet.walletId(), provider, amount);
    }

    private static void fault(int port, String operation, String mode, long delayMs) throws Exception {
        var reply = postRaw(port, "/control/faults", """
                {"operation":"%s","mode":"%s","delay_ms":%d,"count":1}
                """.formatted(operation, mode, delayMs));
        assertThat(reply.status()).as(reply.raw()).isEqualTo(201);
    }

    private static Opened openWallet() throws Exception {
        Reply health;
        try {
            health = send(HttpRequest.newBuilder(URI.create(STACK.walletBase() + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build());
        } catch (Exception failure) {
            throw new AssertionError("wallet health " + STACK.walletBase() + " failed", failure);
        }
        assertThat(health.status()).as(STACK.walletBase() + " " + health.raw()).isEqualTo(200);
        var phone = "+221" + PHONES.getAndIncrement();
        var registered = postWallet("/customers", "{\"phone\":\"" + phone + "\"}", null);
        assertThat(registered.status()).as(registered.raw()).isEqualTo(201);
        var id = registered.body().path("id").asString();
        var otp = registered.body().path("otp").asString();
        var verified = postWallet("/customers/" + id + "/verification",
                "{\"otp\":\"" + otp + "\"}", UUID.randomUUID());
        assertThat(verified.status()).as(verified.raw()).isEqualTo(201);
        var walletId = verified.body().path("wallet_id").asString();
        var alias = verified.body().path("pispi_alias").asString();
        awaitProjection(walletId);
        return new Opened(phone, walletId, alias);
    }

    private static void awaitProjection(String walletId) throws Exception {
        awaitUntil("projection " + walletId, () -> count(
                SenwalletPostgres.PAYMENTS_DATABASE,
                SenwalletPostgres.PAYMENTS_APP,
                SenwalletPostgres.PAYMENTS_APP_PASSWORD,
                "select count(*) from wallet_projections where wallet_id = ?::uuid",
                walletId) == 1);
    }

    private static JsonNode awaitState(String id, String wanted, String forbidden) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode last = JSON.missingNode();
        while (System.nanoTime() < deadline) {
            last = getOrder(id);
            var state = last.path("state").asString();
            if (wanted.equals(state)) {
                return last;
            }
            if (forbidden != null && forbidden.equals(state)) {
                throw new AssertionError("order " + id + " reached " + forbidden + " before " + wanted + ": " + last);
            }
            Thread.sleep(40);
        }
        throw new AssertionError("order " + id + " stayed " + last);
    }

    private static void awaitAvailable(String walletId, long amount) throws Exception {
        awaitUntil("available " + amount + " on " + walletId, () -> {
            try {
                return walletView(walletId).path("balances").path("available").asLong() == amount;
            } catch (Exception failure) {
                return false;
            }
        });
    }

    private static void awaitSuspense(long posted) throws Exception {
        awaitUntil("suspense posted " + posted, () -> {
            try {
                return suspensePosted() == posted;
            } catch (SQLException failure) {
                return false;
            }
        });
    }

    private static void awaitUntil(String what, Probe probe) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (probe.ready()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    private static JsonNode getOrder(String id) throws Exception {
        var reply = send(HttpRequest.newBuilder(URI.create(STACK.paymentsBase() + "/payment-orders/" + id))
                .header("Authorization", "Bearer " + SenwalletKeycloak.customerToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build());
        assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
        return reply.body();
    }

    private static JsonNode walletView(String walletId) throws Exception {
        var reply = send(HttpRequest.newBuilder(URI.create(STACK.walletBase() + "/wallets/" + walletId))
                .header("Authorization", "Bearer " + SenwalletKeycloak.customerToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build());
        assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
        return reply.body();
    }

    private static Reply postPayments(String path, String body, UUID key) throws Exception {
        return send(authorized(STACK.paymentsBase() + path, body, key));
    }

    private static Reply postWallet(String path, String body, UUID key) throws Exception {
        return send(authorized(STACK.walletBase() + path, body, key));
    }

    private static Reply postRaw(int port, String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private static HttpRequest authorized(String url, String body, UUID key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + SenwalletKeycloak.customerToken())
                .timeout(Duration.ofSeconds(20));
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return request.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static Reply send(HttpRequest request) throws Exception {
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        var raw = response.body() == null ? "" : response.body();
        var body = raw.isBlank() ? JSON.missingNode() : JSON.readTree(raw);
        return new Reply(response.statusCode(), body, raw);
    }

    private static String ledgerStatus(String externalId) throws SQLException {
        try (var connection = ledger();
                var statement = connection.prepareStatement(
                        "select status from ledger_transactions where external_id = ?")) {
            statement.setString(1, externalId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static int ledgerCount(String externalId) throws SQLException {
        return count(SenwalletPostgres.LEDGER_DATABASE, SenwalletPostgres.LEDGER_APP,
                SenwalletPostgres.LEDGER_APP_PASSWORD,
                "select count(*) from ledger_transactions where external_id = ?", externalId);
    }

    private static int countPosted(String externalId) throws SQLException {
        return count(SenwalletPostgres.LEDGER_DATABASE, SenwalletPostgres.LEDGER_APP,
                SenwalletPostgres.LEDGER_APP_PASSWORD,
                "select count(*) from ledger_transactions where external_id = ? and status = 'posted'",
                externalId);
    }

    private static int topupCount(String walletId) throws SQLException {
        return count(SenwalletPostgres.PAYMENTS_DATABASE, SenwalletPostgres.PAYMENTS_APP,
                SenwalletPostgres.PAYMENTS_APP_PASSWORD,
                "select count(*) from payment_orders where wallet_id = ?::uuid and type = 'topup'", walletId);
    }

    private static String orderWallet(String externalId) throws SQLException {
        var ref = externalId.substring("pispi-in:".length());
        try (var connection = payments();
                var statement = connection.prepareStatement(
                        "select wallet_id::text from payment_orders where provider_ref = ? or id::text = ?")) {
            statement.setString(1, ref);
            statement.setString(2, ref);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("inbound order " + externalId).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static long suspensePosted() throws SQLException {
        try (var connection = payments();
                var accounts = connection.createStatement();
                var id = accounts.executeQuery("select suspense_account_id from platform_accounts")) {
            assertThat(id.next()).isTrue();
            var account = id.getString(1);
            try (var ledger = ledger();
                    var balances = ledger.prepareStatement(
                            "select posted_cr from account_balances where account_id = ?::uuid")) {
                balances.setString(1, account);
                try (var rows = balances.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getLong(1);
                }
            }
        }
    }

    private static int count(String database, String user, String password, String sql, String argument)
            throws SQLException {
        try (var connection = DriverManager.getConnection(SenwalletPostgres.jdbcUrl(database), user, password);
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, argument);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static java.sql.Connection ledger() throws SQLException {
        return DriverManager.getConnection(
                SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_APP,
                SenwalletPostgres.LEDGER_APP_PASSWORD);
    }

    private static java.sql.Connection payments() throws SQLException {
        return DriverManager.getConnection(
                SenwalletPostgres.jdbcUrl(SenwalletPostgres.PAYMENTS_DATABASE),
                SenwalletPostgres.PAYMENTS_APP,
                SenwalletPostgres.PAYMENTS_APP_PASSWORD);
    }

    private record Opened(String phone, String walletId, String alias) {
    }

    private record Reply(int status, JsonNode body, String raw) {
    }

    @FunctionalInterface
    private interface Probe {
        boolean ready() throws Exception;
    }

    private record Stack(
            ConfigurableApplicationContext payments,
            int paymentsPort,
            int walletPort,
            int orangePort,
            int pispiPort) {

        String paymentsBase() {
            return "http://localhost:" + paymentsPort;
        }

        String walletBase() {
            return "http://localhost:" + walletPort;
        }

        static Stack boot() {
            var ledgerPort = freePort();
            var walletPort = freePort();
            var paymentsPort = freePort();
            var wavePort = freePort();
            var orangePort = freePort();
            var pispiPort = freePort();
            var callback = "http://localhost:" + paymentsPort;

            start(LedgerApplication.class, service("ledger", ledgerPort, SenwalletPostgres.ledgerProperties(),
                    SenwalletKeycloak.ledgerProperties(), Map.of(
                            "spring.flyway.locations", "classpath:db/migration",
                            "ledger.outbox.relay-enabled", "true")));
            start(WalletApplication.class, service("wallet", walletPort, SenwalletPostgres.walletProperties(),
                    SenwalletKeycloak.walletProperties(), Map.of(
                            "spring.flyway.locations", "classpath:db/wallet",
                            "wallet.ledger.base-url", "http://localhost:" + ledgerPort,
                            "wallet.outbox.delay", "200")));
            start(MnoApplication.class, simulator("sim-wave", wavePort, "wave", callback, "/webhooks/mno"));
            start(MnoApplication.class, simulator("sim-orange", orangePort, "orange", callback, "/webhooks/mno"));
            start(PispiApplication.class, simulator("sim-pispi", pispiPort, null, callback, "/webhooks/pispi"));
            var payments = start(PaymentsApplication.class, service("payments", paymentsPort,
                    SenwalletPostgres.paymentsProperties(), SenwalletKeycloak.paymentsProperties(), Map.of(
                            "payments.ledger.base-url", "http://localhost:" + ledgerPort,
                            "payments.rails.wave-url", "http://localhost:" + wavePort,
                            "payments.rails.orange-url", "http://localhost:" + orangePort,
                            "payments.rails.pispi-url", "http://localhost:" + pispiPort,
                            "payments.rails.timeout", "5s",
                            "payments.inquiry.delays", "PT1S,PT1S,PT1S,PT1S,PT1S",
                            "payments.workers.delay", "100")));
            return new Stack(payments, paymentsPort, walletPort, orangePort, pispiPort);
        }

        private static ConfigurableApplicationContext start(Class<?> type, Map<String, Object> properties) {
            return new SpringApplicationBuilder(type)
                    .initializers(context -> context.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("it", properties)))
                    .run();
        }

        private static Map<String, Object> service(
                String name,
                int port,
                Map<String, String> datasource,
                Map<String, String> security,
                Map<String, String> extra) {
            var properties = base(name, port);
            properties.putAll(datasource);
            properties.putAll(security);
            properties.put("spring.rabbitmq.host", SenwalletRabbit.host());
            properties.put("spring.rabbitmq.port", Integer.toString(SenwalletRabbit.port()));
            properties.put("spring.rabbitmq.username", SenwalletRabbit.USER);
            properties.put("spring.rabbitmq.password", SenwalletRabbit.PASSWORD);
            properties.put("spring.rabbitmq.publisher-confirm-type", "correlated");
            properties.putAll(extra);
            return properties;
        }

        private static Map<String, Object> simulator(
                String name, int port, String provider, String callback, String path) {
            var properties = base(name, port);
            properties.put("simulator.callback-base-url", callback);
            properties.put("simulator.callback-path", path);
            properties.put("simulator.hmac-secret", "sim-hmac-secret");
            properties.put("spring.flyway.enabled", "false");
            properties.put("spring.autoconfigure.exclude", String.join(",",
                    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                    "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                    "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
                    "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
                    "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
                    "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
                    "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
                    "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
                    "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
                    "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
                    "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"));
            if (provider != null) {
                properties.put("simulator.provider", provider);
            }
            return properties;
        }

        private static Map<String, Object> base(String name, int port) {
            var properties = new HashMap<String, Object>();
            properties.put("spring.application.name", name);
            properties.put("server.port", Integer.toString(port));
            properties.put("spring.jmx.enabled", "false");
            return properties;
        }

        private static int freePort() {
            try (var socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
