package net.ftgo.accounting.webhook;

public enum PaymentWebhookApplyResult {
    APPLIED,
    DUPLICATE,
    ALREADY_APPLIED,
    IGNORED_STATE_REGRESSION,
    MANUAL_REVIEW_REQUIRED,
    UNKNOWN_AUTHORIZATION
}
