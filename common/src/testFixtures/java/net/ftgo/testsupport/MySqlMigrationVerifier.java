package net.ftgo.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs a service's Flyway migrations against a fresh MySQL database and exposes
 * a JdbcTemplate for schema assertions.
 */
public final class MySqlMigrationVerifier {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");

    private MySqlMigrationVerifier() {
    }

    public static void migrateAndValidate(
            String moduleName,
            String migrationLocation,
            Consumer<JdbcTemplate> assertions) {
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(migrationLocation, "migrationLocation");
        Objects.requireNonNull(assertions, "assertions");

        String databaseName = "ftgo_" + moduleName.replace('-', '_') + "_migration_test";

        try (MySQLContainer<?> mysql = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName(databaseName)
                .withUsername("test")
                .withPassword("test")) {
            mysql.start();

            Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations(migrationLocation)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword());
            dataSource.setDriverClassName(mysql.getDriverClassName());

            assertions.accept(new JdbcTemplate(dataSource));
        }
    }
}
