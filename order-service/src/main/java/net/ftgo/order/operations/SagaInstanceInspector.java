package net.ftgo.order.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SagaInstanceInspector {

    private static final Logger logger = LoggerFactory.getLogger(SagaInstanceInspector.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SagaInstanceInspector(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<SagaSnapshot> findActiveForOrder(Long orderId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT saga_type, saga_id, state_name, end_state, compensating, failed, saga_data_json "
                    + "FROM eventuate.saga_instance WHERE end_state = false"
            );
            for (Map<String, Object> row : rows) {
                String data = String.valueOf(row.get("saga_data_json"));
                JsonNode node = objectMapper.readTree(data);
                if (node.hasNonNull("orderId") && orderId.toString().equals(node.get("orderId").asText())) {
                    return Optional.of(new SagaSnapshot(
                        String.valueOf(row.get("saga_type")),
                        String.valueOf(row.get("saga_id")),
                        String.valueOf(row.get("state_name")),
                        booleanValue(row.get("compensating")),
                        booleanValue(row.get("failed"))
                    ));
                }
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            logger.warn("Unable to inspect Eventuate saga table for order {}", orderId, e);
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Eventuate saga data", e);
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record SagaSnapshot(
        String sagaType,
        String sagaId,
        String stateName,
        boolean compensating,
        boolean failed
    ) {
    }
}
