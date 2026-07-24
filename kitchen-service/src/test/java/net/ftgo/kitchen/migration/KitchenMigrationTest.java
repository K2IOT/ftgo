package net.ftgo.kitchen.migration;

import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KitchenMigrationTest {

    @Test
    void freshSchemaContainsPendingRevisionState() {
        MySqlMigrationVerifier.migrateAndValidate(
            "kitchen-service",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(columnExistsInCurrentDatabase(
                    jdbc,
                    "tickets",
                    "previous_state"
                )).isTrue();
                assertThat(columnDataTypeInCurrentDatabase(
                    jdbc,
                    "tickets",
                    "state"
                )).isEqualTo("enum");
                assertThat(columnDataTypeInCurrentDatabase(
                    jdbc,
                    "tickets",
                    "previous_state"
                )).isEqualTo("enum");
                assertThat(tableExistsInCurrentDatabase(
                    jdbc,
                    "ticket_pending_line_items"
                )).isTrue();
            }
        );
    }

    @Test
    void ticketStateColumnsSupportTheCompleteDomainLifecycle() {
        MySqlMigrationVerifier.migrateAndValidate(
            "kitchen-service-ticket-state",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                String stateType = columnTypeInCurrentDatabase(jdbc, "tickets", "state");
                String previousStateType = columnTypeInCurrentDatabase(
                    jdbc,
                    "tickets",
                    "previous_state"
                );

                Arrays.stream(TicketState.values())
                    .map(TicketState::name)
                    .forEach(state -> {
                        assertThat(stateType).contains("'" + state + "'");
                        assertThat(previousStateType).contains("'" + state + "'");
                    });
            }
        );
    }

    @Test
    void freshSchemaContainsEventuateMessagingTables() {
        MySqlMigrationVerifier.migrateAndValidate(
            "kitchen-service-eventuate",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(tableExistsInCurrentDatabase(jdbc, "message")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "received_messages")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "offset_store")).isTrue();
            }
        );
    }

    private static boolean tableExistsInCurrentDatabase(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
            "select count(*) from information_schema.tables " +
                "where table_schema = database() and table_name = ?",
            Integer.class,
            table
        );
        return count != null && count == 1;
    }

    private static boolean columnExistsInCurrentDatabase(
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

    private static String columnDataTypeInCurrentDatabase(
        JdbcTemplate jdbc,
        String table,
        String column
    ) {
        return jdbc.queryForObject(
            "select data_type from information_schema.columns " +
                "where table_schema = database() and table_name = ? and column_name = ?",
            String.class,
            table,
            column
        );
    }

    private static String columnTypeInCurrentDatabase(
        JdbcTemplate jdbc,
        String table,
        String column
    ) {
        return jdbc.queryForObject(
            "select column_type from information_schema.columns " +
                "where table_schema = database() and table_name = ? and column_name = ?",
            String.class,
            table,
            column
        );
    }
}
