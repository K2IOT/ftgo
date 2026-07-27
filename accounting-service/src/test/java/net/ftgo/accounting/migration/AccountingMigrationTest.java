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
                    .contains("PARTIALLY_REFUNDED")
                    .contains("VOIDED")
                    .contains("REFUNDED")
                    .contains("APPROVED")
                    .contains("REVERSED");
            });
    }

    @Test
    void freshSchemaContainsPaymentSettlementTablesAndIndexes() {
        MySqlMigrationVerifier.migrateAndValidate(
            "accounting-service-payment-settlement",
            "filesystem:src/main/resources/db/migration",
            jdbc -> {
                assertThat(tableExistsInCurrentDatabase(jdbc, "payment_ledger_entries")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "payment_refunds")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "simulated_provider_payments")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "simulated_provider_operations")).isTrue();
                assertThat(tableExistsInCurrentDatabase(jdbc, "settlement_discrepancies")).isTrue();

                assertThat(columnType(jdbc, "payment_ledger_entries", "operation_type"))
                    .startsWith("varchar");
                assertThat(columnType(jdbc, "simulated_provider_payments", "status"))
                    .startsWith("varchar");
                assertThat(columnType(jdbc, "settlement_discrepancies", "status"))
                    .startsWith("varchar");
            });
    }

    private static String columnType(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
            "select column_type from information_schema.columns " +
                "where table_schema = database() and table_name = ? and column_name = ?",
            String.class,
            table,
            column
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
}