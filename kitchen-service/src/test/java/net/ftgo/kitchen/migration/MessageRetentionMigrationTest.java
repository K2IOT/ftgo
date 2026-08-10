package net.ftgo.kitchen.migration;

import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRetentionMigrationTest {

    @Test
    void freshSchemaContainsMessageRetentionIndexes() {
        MySqlMigrationVerifier.migrateAndValidate(
            "kitchen-service-retention-indexes",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(indexColumns(jdbc, "outbox", "idx_outbox_created_at"))
                    .isEqualTo("created_at");
                assertThat(indexColumns(
                    jdbc,
                    "processed_commands",
                    "idx_processed_commands_outcome_processed_at"
                )).isEqualTo("outcome,processed_at");
            }
        );
    }

    private static String indexColumns(JdbcTemplate jdbc, String table, String index) {
        return jdbc.queryForObject(
            "select group_concat(column_name order by seq_in_index separator ',') " +
                "from information_schema.statistics " +
                "where table_schema = database() and table_name = ? and index_name = ?",
            String.class,
            table,
            index
        );
    }
}
