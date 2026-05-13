package net.ftgo.common.orderflow.replies;

public class CardAuthorized {

    private Long authorizationId;

    public CardAuthorized() {
    }

    public CardAuthorized(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
