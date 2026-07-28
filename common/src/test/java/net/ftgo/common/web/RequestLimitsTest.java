package net.ftgo.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLimitsTest {

    @Test
    void acceptsPrintableSafeIdempotencyKeyWithinBounds() {
        assertThat(RequestLimits.isSafeIdempotencyKey("refund-12345678")).isTrue();
    }

    @Test
    void rejectsShortWhitespaceAndControlCharacterKeys() {
        assertThat(RequestLimits.isSafeIdempotencyKey("short")).isFalse();
        assertThat(RequestLimits.isSafeIdempotencyKey("refund key 123")).isFalse();
        assertThat(RequestLimits.isSafeIdempotencyKey("refund-123\n456")).isFalse();
    }

    @Test
    void rejectsKeyLongerThanMaximum() {
        assertThat(RequestLimits.isSafeIdempotencyKey("x".repeat(256))).isFalse();
    }
}
