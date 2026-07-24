package net.ftgo.accounting.migration;

import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingMigrationTest {

    @Test
    void freshSchemaContainsEventuateMessagingTables() {
        MySqlMigrationVerifier.migrateAndValidate(
            "accounting-service",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(tableExistsInCurrentDatabase(jdbc, "message")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "received_messages")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "offset_store")).isTrue();
            });
    }

    @Test
    void authorizationStatusEnumSupportsTheCompleteLifecycle() {
        MySqlMigrationVerifier.migrateAndValidate(
            "accounting-service-status-enum",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                String columnType = jdbc.queryForObject(
                    "select column_type from information_schema.columns " +
                        "where table_schema = database() " +
                        "and table_name = 'authorizations' and column_name = 'status'",
                    String.class
                );

                assertThat(columnType)
                    .contains("AUTHORIZED")
                    .contains("DENIED")
                    .contains("CAPTURED")
                    .contains("VOIDED")
                    .contains("REFUNDED")
                    .contains("APPROVED")
                    .contains("REVERSED");
            });
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
}
