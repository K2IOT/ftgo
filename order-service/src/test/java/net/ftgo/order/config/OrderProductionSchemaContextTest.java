package net.ftgo.order.config;

import net.ftgo.order.saga.OrderServiceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProductionSchemaContextTest extends OrderServiceIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextUsesProductionFlywayMigrations() {
        assertThat(tableExists("flyway_schema_history")).isTrue();
        assertThat(tableExists("message")).isTrue();
        assertThat(tableExists("received_messages")).isTrue();
        assertThat(columnExists("saga_instance", "end_state")).isTrue();
        assertThat(columnExists("saga_instance", "compensating")).isTrue();
        assertThat(columnExists("saga_instance", "failed")).isTrue();
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables " +
                        "where table_schema = database() and table_name = ?",
                Integer.class,
                table);
        return count != null && count == 1;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns " +
                        "where table_schema = database() and table_name = ? and column_name = ?",
                Integer.class,
                table,
                column);
        return count != null && count == 1;
    }
}
