package sn.senwallet.testsupport;

import java.util.Map;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * One RabbitMQ 4 broker per test JVM. The default {@code guest} user is
 * refused from outside the container, so tests connect as {@code senwallet}.
 */
public final class SenwalletRabbit {

    public static final String USER = "senwallet";
    public static final String PASSWORD = "senwallet";

    private static final RabbitMQContainer CONTAINER = start();

    private SenwalletRabbit() {
    }

    public static String host() {
        return CONTAINER.getHost();
    }

    public static int port() {
        return CONTAINER.getAmqpPort();
    }

    /** Spring properties for the ledger relay. */
    public static Map<String, String> properties() {
        return Map.of(
                "spring.rabbitmq.host", host(),
                "spring.rabbitmq.port", Integer.toString(port()),
                "spring.rabbitmq.username", USER,
                "spring.rabbitmq.password", PASSWORD,
                "spring.rabbitmq.publisher-confirm-type", "correlated");
    }

    private static RabbitMQContainer start() {
        var container = new RabbitMQContainer("rabbitmq:4-alpine")
                .withAdminUser(USER)
                .withAdminPassword(PASSWORD);
        container.start();
        return container;
    }
}
