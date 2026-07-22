package net.ftgo.kitchen.migration;

import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
                            "previous_state")).isTrue();
                    assertThat(tableExistsInCurrentDatabase(
                            jdbc,
                            "ticket_pending_line_items")).isTrue();
                });
    }

    private static boolean tableExistsInCurrentDatabase(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables " +
                        "where table_schema = database() and table_name = ?",
                Integer.class,
                table);
        return count != null && count == 1;
    }

    private static boolean columnExistsInCurrentDatabase(
            JdbcTemplate jdbc,
            String table,
            String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns " +
                        "where table_schema = database() and table_name = ? and column_name = ?",
                Integer.class,
                table,
                column);
        return count != null && count == 1;
    }
}
