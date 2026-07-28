package net.ftgo.e2e.support;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestIdentityProviderTest {

    @Test
    void servesJwksAndSignsDomainClaims() throws Exception {
        try (TestIdentityProvider identityProvider = TestIdentityProvider.start(0)) {
            String token = identityProvider.issueToken(
                "consumer-41",
                List.of("CONSUMER"),
                List.of("ftgo-api"),
                Map.of("consumer_id", 41L),
                Duration.ofMinutes(5)
            );

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(identityProvider.jwkSetUri())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).isEqualTo(200);

            RSAKey publicKey = (RSAKey) JWKSet.parse(response.body())
                .getKeyByKeyId(identityProvider.keyId());
            SignedJWT signedJWT = SignedJWT.parse(token);

            assertThat(signedJWT.verify(new RSASSAVerifier(publicKey))).isTrue();
            assertThat(signedJWT.getJWTClaimsSet().getIssuer())
                .isEqualTo(identityProvider.issuer());
            assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("consumer-41");
            assertThat(signedJWT.getJWTClaimsSet().getAudience()).containsExactly("ftgo-api");
            assertThat(signedJWT.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("CONSUMER");
            assertThat(signedJWT.getJWTClaimsSet().getLongClaim("consumer_id")).isEqualTo(41L);
        }
    }

    @Test
    void canIssueExpiredAndWrongAudienceTokensForNegativeScenarios() throws Exception {
        try (TestIdentityProvider identityProvider = TestIdentityProvider.start(0)) {
            SignedJWT expired = SignedJWT.parse(identityProvider.issueToken(
                "consumer-41",
                List.of("CONSUMER"),
                List.of("ftgo-api"),
                Map.of("consumer_id", 41L),
                Duration.ofSeconds(-30)
            ));
            SignedJWT wrongAudience = SignedJWT.parse(identityProvider.issueToken(
                "consumer-41",
                List.of("CONSUMER"),
                List.of("another-api"),
                Map.of("consumer_id", 41L),
                Duration.ofMinutes(5)
            ));

            assertThat(expired.getJWTClaimsSet().getExpirationTime().toInstant())
                .isBefore(Instant.now());
            assertThat(wrongAudience.getJWTClaimsSet().getAudience())
                .containsExactly("another-api");
        }
    }
}
