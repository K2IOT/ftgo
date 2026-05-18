package net.ftgo.common.orderflow.replies;

public class TicketCancellationRefused {

    public static final String PREPARATION_ALREADY_STARTED = "PREPARATION_ALREADY_STARTED";

    private String reasonCode;
    private String message;

    public TicketCancellationRefused() {
    }

    public TicketCancellationRefused(String reasonCode, String message) {
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
