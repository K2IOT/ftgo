package net.ftgo.order.migration;

import net.ftgo.order.domain.OrderState;
import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMigrationTest {

    @Test
    void freshSchemaContainsEventuateMessagingAndSagaTables() {
        MySqlMigrationVerifier.migrateAndValidate(
            "order-service",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(tableExists(jdbc, "message")).isTrue();
                assertThat(tableExists(jdbc, "received_messages")).isTrue();
                assertThat(tableExists(jdbc, "offset_store")).isTrue();
                assertThat(tableExists(jdbc, "saga_instance")).isTrue();
                assertThat(tableExists(jdbc, "saga_instance_participants")).isTrue();

                assertThat(columnExists(jdbc, "saga_instance", "end_state")).isTrue();
                assertThat(columnExists(jdbc, "saga_instance", "compensating")).isTrue();
                assertThat(columnExists(jdbc, "saga_instance", "failed")).isTrue();
            }
        );
    }

    @Test
    void orderStateColumnSupportsTheCompleteDomainLifecycle() {
        MySqlMigrationVerifier.migrateAndValidate(
            "order-service-order-state",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                String columnType = jdbc.queryForObject(
                    "select column_type from information_schema.columns " +
                        "where table_schema = database() " +
                        "and table_name = 'orders' and column_name = 'state'",
                    String.class
                );

                Arrays.stream(OrderState.values())
                    .map(OrderState::name)
                    .forEach(state -> assertThat(columnType).contains("'" + state + "'"));
            }
        );
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
            "select count(*) from information_schema.tables " +
                "where table_schema = database() and table_name = ?",
            Integer.class,
            table
        );
        return count != null && count == 1;
    }

    private static boolean columnExists(
        JdbcTemplate jdbc,
        String table,
        String column
    ) {
        Integer count = jdbc.queryForObject(
            "select count(*) from information_schema.columns " +
                "where table_schema = database() and table_name = ? and column_name = ?",
            Integer.class,
            table,
            column
        );
        return count != null && count == 1;
    }
}
