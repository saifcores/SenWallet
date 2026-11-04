package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import sn.senwallet.events.inbox.IdempotentInbox;
import sn.senwallet.ledger.application.port.in.PostTransactionUseCase;
import sn.senwallet.ledger.domain.model.account.NormalBalance;
import sn.senwallet.ledger.domain.model.transaction.TransactionStatus;
import sn.senwallet.ledger.infrastructure.adapter.out.outbox.EventPublisher;
import sn.senwallet.ledger.infrastructure.adapter.out.outbox.OutboxMessage;
import sn.senwallet.ledger.infrastructure.adapter.out.outbox.OutboxRelay;
import sn.senwallet.ledger.infrastructure.adapter.out.outbox.RabbitEventPublisher;
import sn.senwallet.shared.id.AccountId;
import sn.senwallet.shared.id.LedgerId;
import sn.senwallet.testsupport.SenwalletPostgres;
import sn.senwallet.testsupport.SenwalletRabbit;

/**
 * Stops the relay after the broker has accepted part of a batch, then runs it
 * again. The consumer applies each event once.
 */
@Import(OutboxRelayIT.RelayControl.class)
class OutboxRelayIT extends LedgerIntegrationTest {

    private static final String QUEUE = "ledger.it.outbox";
    private static final String PERMISSION_DENIED = "42501";

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        SenwalletRabbit.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    PostTransactionUseCase post;

    @Autowired
    OutboxRelay relay;

    @Autowired
    StoppingPublisher stopping;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin rabbitAdmin;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transactions;

    private IdempotentInbox inbox;
    private LedgerId ledger;
    private AccountId treasury;
    private AccountId wallet;

    @BeforeEach
    void setUp() throws Exception {
        inboxTables();
        inbox = new IdempotentInbox(jdbc);
        ledger = newLedger();
        treasury = openAccount(ledger, "treasury", NormalBalance.DEBIT);
        wallet = openAccount(ledger, "wallet", NormalBalance.CREDIT);
        try (var db = ownerConnection(); var statement = db.createStatement()) {
            statement.execute("UPDATE outbox SET published_at = now() WHERE published_at IS NULL");
        }
        var exchange = new TopicExchange(RabbitEventPublisher.EXCHANGE, true, false);
        rabbitAdmin.declareExchange(exchange);
        var queue = QueueBuilder.durable(QUEUE).build();
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("ledger.#"));
        rabbitAdmin.purgeQueue(QUEUE);
        stopping.passThrough();
    }

    @Test
    void aStoppedBatchIsRedeliveredAndAppliedOnce() {
        for (var i = 0; i < 5; i++) {
            post.post(CountersMatchEntriesIT.command(ledger, "outbox:" + i, treasury, wallet, 1_000,
                    TransactionStatus.POSTED, List.of()));
        }
        assertThat(unpublished()).isEqualTo(5);

        stopping.failAfter(2);
        assertThatThrownBy(() -> relay.publishBatch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mid-batch");
        assertThat(unpublished()).isEqualTo(5);

        var first = drain();
        assertThat(first).isEqualTo(2);

        stopping.passThrough();
        assertThat(relay.publishBatch()).isEqualTo(5);
        assertThat(unpublished()).isZero();

        var second = drain();
        assertThat(second).isEqualTo(3);
        assertThat(applied()).isEqualTo(5);
        assertThat(processed()).isEqualTo(5);
    }

    @Test
    void aFailedHandlerRollsTheClaimBack() {
        var eventId = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> inbox.claim(eventId, () -> {
            throw new IllegalStateException("boom");
        }))).hasMessageContaining("boom");

        var stored = jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", eventId)
                .query(Long.class)
                .single();
        assertThat(stored).isZero();
    }

    @Test
    void theApplicationRoleCannotChangeOrDeleteOutboxRows() throws SQLException {
        post.post(CountersMatchEntriesIT.command(ledger, "outbox:guard", treasury, wallet, 1_000,
                TransactionStatus.POSTED, List.of()));

        try (var db = appConnection()) {
            assertSqlState(db, "UPDATE outbox SET payload = '{}'::jsonb", PERMISSION_DENIED);
            assertSqlState(db, "DELETE FROM outbox", PERMISSION_DENIED);
        }
    }

    private static void inboxTables() throws Exception {
        try (var in = IdempotentInbox.class.getResourceAsStream("/db/processed_events.sql")) {
            if (in == null) {
                throw new IllegalStateException("processed_events.sql is missing");
            }
            var sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (var db = ownerConnection(); var statement = db.createStatement()) {
                statement.execute(sql);
                statement.execute("GRANT SELECT, INSERT ON processed_events TO " + SenwalletPostgres.LEDGER_APP);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS applied_events (
                            event_id uuid PRIMARY KEY
                        )
                        """);
                statement.execute("GRANT SELECT, INSERT ON applied_events TO " + SenwalletPostgres.LEDGER_APP);
            }
        }
    }

    private int drain() {
        var appliedNow = new AtomicInteger();
        org.springframework.amqp.core.Message message;
        while ((message = rabbitTemplate.receive(QUEUE, 500)) != null) {
            var eventId = UUID.fromString(message.getMessageProperties().getMessageId());
            var claimed = transactions.execute(status -> inbox.claim(eventId, () -> jdbc.sql("""
                    INSERT INTO applied_events (event_id) VALUES (:id)
                    """).param("id", eventId).update()));
            if (Boolean.TRUE.equals(claimed)) {
                appliedNow.incrementAndGet();
            }
        }
        return appliedNow.get();
    }

    private long unpublished() {
        return jdbc.sql("""
                SELECT count(*) FROM outbox
                WHERE published_at IS NULL
                  AND aggregate_id IN (SELECT id FROM ledger_transactions WHERE ledger_id = :ledger)
                """)
                .param("ledger", ledger.value())
                .query(Long.class)
                .single();
    }

    private long applied() {
        return countEvents("applied_events");
    }

    private long processed() {
        return countEvents("processed_events");
    }

    private long countEvents(String table) {
        return jdbc.sql("""
                SELECT count(*) FROM %s
                WHERE event_id IN (
                    SELECT event_id FROM outbox
                    WHERE aggregate_id IN (SELECT id FROM ledger_transactions WHERE ledger_id = :ledger)
                )
                """.formatted(table))
                .param("ledger", ledger.value())
                .query(Long.class)
                .single();
    }

    private static void assertSqlState(java.sql.Connection db, String sql, String sqlState) {
        assertThatThrownBy(() -> {
            try (var statement = db.createStatement()) {
                statement.execute(sql);
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as(sql).isEqualTo(sqlState));
    }

    private static java.sql.Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_OWNER, SenwalletPostgres.LEDGER_OWNER_PASSWORD);
    }

    private static java.sql.Connection appConnection() throws SQLException {
        return DriverManager.getConnection(SenwalletPostgres.jdbcUrl(SenwalletPostgres.LEDGER_DATABASE),
                SenwalletPostgres.LEDGER_APP, SenwalletPostgres.LEDGER_APP_PASSWORD);
    }

    @TestConfiguration
    static class RelayControl {

        @Bean
        @Primary
        StoppingPublisher stoppingPublisher(RabbitEventPublisher rabbit) {
            return new StoppingPublisher(rabbit);
        }

        @Bean
        TransactionTemplate outboxTransactions(org.springframework.transaction.PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    static final class StoppingPublisher implements EventPublisher {

        private final EventPublisher delegate;
        private int remaining = Integer.MAX_VALUE;

        StoppingPublisher(EventPublisher delegate) {
            this.delegate = delegate;
        }

        void failAfter(int successes) {
            this.remaining = successes;
        }

        void passThrough() {
            this.remaining = Integer.MAX_VALUE;
        }

        @Override
        public void publish(List<OutboxMessage> messages) {
            if (remaining >= messages.size()) {
                delegate.publish(messages);
                remaining -= messages.size();
                return;
            }
            if (remaining > 0) {
                delegate.publish(List.copyOf(messages.subList(0, remaining)));
                remaining = 0;
            }
            throw new IllegalStateException("relay stopped mid-batch");
        }
    }
}
