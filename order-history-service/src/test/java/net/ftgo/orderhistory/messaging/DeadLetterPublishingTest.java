package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.core.JsonParseException;
import net.ftgo.common.messaging.KafkaDeadLetterSupport;
import net.ftgo.common.messaging.NonRetryableEventException;
import net.ftgo.common.messaging.RetryableEventException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadLetterPublishingTest {

    @Test
    void classifiesContractFailuresAsNonRetryableAndTransientFailuresAsRetryable() {
        assertFalse(KafkaDeadLetterSupport.isRetryable(
            new NonRetryableEventException("schema mismatch")
        ));
        assertFalse(KafkaDeadLetterSupport.isRetryable(
            new IllegalArgumentException("event identity mismatch")
        ));
        assertFalse(KafkaDeadLetterSupport.isRetryable(
            new JsonParseException(null, "malformed JSON")
        ));
        assertTrue(KafkaDeadLetterSupport.isRetryable(
            new RetryableEventException("projection dependency temporarily unavailable")
        ));
    }
}
