package sn.senwallet.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * A PostgreSQL 16 container started once per JVM and initialised by the same
 * scripts as Docker Compose ({@code deploy/postgres/init}), so tests see the
 * real roles and databases.
 */
public final class SenwalletPostgres {

    public static final String LEDGER_DATABASE = "ledger";
    public static final String LEDGER_OWNER = "ledger_owner";
    public static final String LEDGER_OWNER_PASSWORD = "ledger_owner_it";
    public static final String LEDGER_APP = "ledger_app";
    public static final String LEDGER_APP_PASSWORD = "ledger_app_it";
    public static final String WALLET_DATABASE = "wallet";
    public static final String WALLET_OWNER = "wallet_owner";
    public static final String WALLET_OWNER_PASSWORD = "wallet_owner_it";
    public static final String WALLET_APP = "wallet_app";
    public static final String WALLET_APP_PASSWORD = "wallet_app_it";

    private static final PostgreSQLContainer CONTAINER = start();

    private SenwalletPostgres() {
    }

    /** JDBC URL of a database in the shared container. */
    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + CONTAINER.getHost() + ":"
                + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
    }

    /**
     * Spring properties for the ledger service: the datasource connects as the
     * application role, Flyway as the owner role.
     */
    public static Map<String, String> ledgerProperties() {
        return Map.of(
                "spring.datasource.url", jdbcUrl(LEDGER_DATABASE),
                "spring.datasource.username", LEDGER_APP,
                "spring.datasource.password", LEDGER_APP_PASSWORD,
                "spring.flyway.user", LEDGER_OWNER,
                "spring.flyway.password", LEDGER_OWNER_PASSWORD);
    }

    /** Spring properties for the wallet service, same role split as the ledger. */
    public static Map<String, String> walletProperties() {
        return Map.of(
                "spring.datasource.url", jdbcUrl(WALLET_DATABASE),
                "spring.datasource.username", WALLET_APP,
                "spring.datasource.password", WALLET_APP_PASSWORD,
                "spring.flyway.user", WALLET_OWNER,
                "spring.flyway.password", WALLET_OWNER_PASSWORD);
    }

    private static PostgreSQLContainer start() {
        var container = new PostgreSQLContainer("postgres:16")
                .withUsername("senwallet")
                .withPassword("senwallet")
                .withDatabaseName("postgres")
                .withEnv("LEDGER_OWNER_PASSWORD", LEDGER_OWNER_PASSWORD)
                .withEnv("LEDGER_DB_PASSWORD", LEDGER_APP_PASSWORD)
                .withEnv("WALLET_OWNER_PASSWORD", WALLET_OWNER_PASSWORD)
                .withEnv("WALLET_DB_PASSWORD", WALLET_APP_PASSWORD)
                .withCopyFileToContainer(MountableFile.forHostPath(initScripts(), 0755),
                        "/docker-entrypoint-initdb.d/");
        container.start();
        return container;
    }

    private static Path initScripts() {
        var root = System.getProperty("senwallet.root");
        if (root == null) {
            throw new IllegalStateException("system property senwallet.root is not set (see senwallet.integration-test)");
        }
        var scripts = Path.of(root, "deploy", "postgres", "init");
        if (!Files.isDirectory(scripts)) {
            throw new IllegalStateException("init scripts not found: " + scripts);
        }
        return scripts;
    }
}
