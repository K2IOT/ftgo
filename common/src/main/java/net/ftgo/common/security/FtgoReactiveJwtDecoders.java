package net.ftgo.common.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FtgoReactiveJwtDecoders {

    private FtgoReactiveJwtDecoders() {
    }

    public static ReactiveJwtDecoder create(
        String issuerUri,
        String jwkSetUri,
        Collection<String> acceptedAudiences
    ) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("issuerUri is required");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("jwkSetUri is required");
        }

        Set<String> audiences = sanitizeAudiences(acceptedAudiences);
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted audience is required");
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().stream()
            .anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT audience is not accepted",
                    null
                ));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            issuerValidator,
            audienceValidator
        ));
        return decoder;
    }

    private static Set<String> sanitizeAudiences(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return Set.copyOf(result);
    }
}
