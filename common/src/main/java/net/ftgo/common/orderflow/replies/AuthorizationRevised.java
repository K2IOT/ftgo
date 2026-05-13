package net.ftgo.common.orderflow.replies;

public class AuthorizationRevised {

    private Long authorizationId;

    public AuthorizationRevised() {
    }

    public AuthorizationRevised(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
