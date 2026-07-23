package net.ftgo.order.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderServiceMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("ftgo_order")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migrationCreatesOrderColumnsNeededBySagaState() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             ResultSet columns = connection.getMetaData().getColumns(null, null, "orders", null)) {

            Set<String> columnNames = new LinkedHashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertThat(columnNames)
                    .contains("ticket_id", "authorization_id");
        }
    }
}
