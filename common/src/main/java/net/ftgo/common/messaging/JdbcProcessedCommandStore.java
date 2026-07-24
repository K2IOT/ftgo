package net.ftgo.common.messaging;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/** MySQL-backed command claim and replay store. */
public class JdbcProcessedCommandStore implements ProcessedCommandStore {

    private static final String PROCESSING = "PROCESSING";

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedCommandStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryStart(String consumerName, String commandId) {
        int inserted = jdbcTemplate.update(
            """
            INSERT IGNORE INTO processed_commands
              (consumer_name, command_id, outcome, reply_type, reply_payload, processed_at)
            VALUES (?, ?, ?, NULL, NULL, CURRENT_TIMESTAMP(6))
            """,
            consumerName,
            commandId,
            PROCESSING
        );
        return inserted == 1;
    }

    @Override
    public void complete(
        String consumerName,
        String commandId,
        ProcessedCommandResult result
    ) {
        int updated = jdbcTemplate.update(
            """
            UPDATE processed_commands
               SET outcome = ?, reply_type = ?, reply_payload = ?, processed_at = CURRENT_TIMESTAMP(6)
             WHERE consumer_name = ?
               AND command_id = ?
               AND outcome = ?
            """,
            result.outcome(),
            result.replyType(),
            result.replyPayload(),
            consumerName,
            commandId,
            PROCESSING
        );
        if (updated != 1) {
            throw new IllegalStateException(
                "Processed command claim was not completed: " + consumerName + "/" + commandId
            );
        }
    }

    @Override
    public Optional<ProcessedCommandResult> findCompleted(
        String consumerName,
        String commandId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT outcome, reply_type, reply_payload
                  FROM processed_commands
                 WHERE consumer_name = ?
                   AND command_id = ?
                   AND outcome <> ?
                """,
                (resultSet, rowNumber) -> new ProcessedCommandResult(
                    resultSet.getString("outcome"),
                    resultSet.getString("reply_type"),
                    resultSet.getString("reply_payload")
                ),
                consumerName,
                commandId,
                PROCESSING
            ));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }
}
