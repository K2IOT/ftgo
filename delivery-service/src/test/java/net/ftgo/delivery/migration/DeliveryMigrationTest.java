package net.ftgo.delivery.migration;

import net.ftgo.testsupport.MySqlMigrationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMigrationTest {

    @Test
    void freshSchemaUsesNativeDeliveryStatusEnum() {
        MySqlMigrationVerifier.migrateAndValidate(
                "delivery-service",
                "filesystem:src/main/resources/db/migration",
                jdbc -> assertThat(columnDataTypeInCurrentDatabase(
                        jdbc,
                        "deliveries",
                        "status")).isEqualTo("enum"));
    }

    private static String columnDataTypeInCurrentDatabase(
            JdbcTemplate jdbc,
            String table,
            String column) {
        return jdbc.queryForObject(
                "select data_type from information_schema.columns " +
                        "where table_schema = database() and table_name = ? and column_name = ?",
                String.class,
                table,
                column);
    }
}
