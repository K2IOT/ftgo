package net.ftgo.order.migration;

import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMigrationTest {

    @Test
    void freshSchemaContainsEventuateMessagingAndSagaTables() {
        MySqlMigrationVerifier.migrateAndValidate(
                "order-service",
                "classpath:db/migration",
                jdbc -> {
                    assertThat(tableExists(jdbc, "eventuate", "message")).isTrue();
                    assertThat(tableExists(jdbc, "eventuate", "received_messages")).isTrue();
                    assertThat(tableExists(jdbc, "eventuate", "saga_instance")).isTrue();
                    assertThat(tableExists(jdbc, "eventuate", "saga_instance_participants")).isTrue();

                    assertThat(columnExists(jdbc, "eventuate", "saga_instance", "end_state")).isTrue();
                    assertThat(columnExists(jdbc, "eventuate", "saga_instance", "compensating")).isTrue();
                    assertThat(columnExists(jdbc, "eventuate", "saga_instance", "failed")).isTrue();
                });
    }

    private static boolean tableExists(
            JdbcTemplate jdbc,
            String schema,
            String table) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables " +
                        "where table_schema = ? and table_name = ?",
                Integer.class,
                schema,
                table);
        return count != null && count == 1;
    }

    private static boolean columnExists(
            JdbcTemplate jdbc,
            String schema,
            String table,
            String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns " +
                        "where table_schema = ? and table_name = ? and column_name = ?",
                Integer.class,
                schema,
                table,
                column);
        return count != null && count == 1;
    }
}
