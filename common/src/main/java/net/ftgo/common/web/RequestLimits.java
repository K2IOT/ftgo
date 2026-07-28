package net.ftgo.common.web;

import java.util.regex.Pattern;

/** Shared limits for public HTTP request validation and gateway policy. */
public final class RequestLimits {

    public static final int MAX_REQUEST_BYTES = 256 * 1024;
    public static final int MAX_ORDER_ITEMS = 50;
    public static final int MAX_ITEM_QUANTITY = 100;
    public static final int MAX_TEXT_LENGTH = 200;
    public static final int MAX_ADDRESS_LINE_LENGTH = 200;
    public static final int MAX_CITY_OR_STATE_LENGTH = 100;
    public static final int MAX_POSTAL_CODE_LENGTH = 20;
    public static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private static final Pattern SAFE_IDEMPOTENCY_KEY = Pattern.compile(
        "[\\x21-\\x7E]{" + MIN_IDEMPOTENCY_KEY_LENGTH + ","
            + MAX_IDEMPOTENCY_KEY_LENGTH + "}"
    );

    private RequestLimits() {
    }

    public static boolean isSafeIdempotencyKey(String value) {
        return value != null && SAFE_IDEMPOTENCY_KEY.matcher(value).matches();
    }
}
