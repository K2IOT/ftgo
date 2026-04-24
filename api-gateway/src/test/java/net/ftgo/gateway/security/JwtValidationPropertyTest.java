package net.ftgo.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.jqwik.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Property-based test for JWT Validation Correctness.
 * 
 * Property 7: JWT Validation Correctness
 * Validates: Requirements 10.8
 * 
 * Test that JWT is accepted iff signature is valid AND not expired.
 */
@Tag("Feature: ftgo-microservices-platform, Property 7: JWT Validation Correctness")
class JwtValidationPropertyTest {
    
    private static RSAPrivateKey validPrivateKey;
    private static RSAPublicKey validPublicKey;
    private static RSAPrivateKey invalidPrivateKey;
    private static ReactiveJwtDecoder jwtDecoder;
    
    static {
        try {
            // Generate valid key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair validKeyPair = keyPairGenerator.generateKeyPair();
            validPrivateKey = (RSAPrivateKey) validKeyPair.getPrivate();
            validPublicKey = (RSAPublicKey) validKeyPair.getPublic();
            
            // Generate invalid key pair (different from valid)
            KeyPair invalidKeyPair = keyPairGenerator.generateKeyPair();
            invalidPrivateKey = (RSAPrivateKey) invalidKeyPair.getPrivate();
            
            // Create decoder with valid public key
            jwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(validPublicKey).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize keys", e);
        }
    }
    
    /**
     * Property: JWT is accepted iff signature is valid AND not expired.
     * 
     * This property tests all four combinations:
     * 1. Valid signature + Not expired = ACCEPTED
     * 2. Valid signature + Expired = REJECTED
     * 3. Invalid signature + Not expired = REJECTED
     * 4. Invalid signature + Expired = REJECTED
     */
    @Property(tries = 100)
    void jwtIsAcceptedIffSignatureValidAndNotExpired(
        @ForAll("subjects") String subject,
        @ForAll boolean validSignature,
        @ForAll boolean notExpired
    ) throws Exception {
        // Create JWT with specified properties
        String token = createJwt(subject, validSignature, notExpired);
        
        // Try to decode the JWT
        boolean accepted = false;
        try {
            Jwt jwt = jwtDecoder.decode(token).block();
            accepted = (jwt != null);
        } catch (Exception e) {
            accepted = false;
        }
        
        // Property: JWT is accepted iff (signature is valid AND not expired)
        boolean shouldBeAccepted = validSignature && notExpired;
        
        if (shouldBeAccepted) {
            // Should be accepted
            if (!accepted) {
                throw new AssertionError(
                    String.format("JWT with valid signature and not expired should be accepted. " +
                        "Subject: %s, ValidSignature: %b, NotExpired: %b",
                        subject, validSignature, notExpired)
                );
            }
        } else {
            // Should be rejected
            if (accepted) {
                throw new AssertionError(
                    String.format("JWT should be rejected. " +
                        "Subject: %s, ValidSignature: %b, NotExpired: %b",
                        subject, validSignature, notExpired)
                );
            }
        }
    }
    
    /**
     * Property: Valid signature and not expired always results in acceptance.
     */
    @Property(tries = 100)
    void validSignatureAndNotExpiredAlwaysAccepted(
        @ForAll("subjects") String subject,
        @ForAll("roles") List<String> roles
    ) throws Exception {
        String token = createValidToken(subject, roles);
        
        Jwt jwt = jwtDecoder.decode(token).block();
        
        if (jwt == null) {
            throw new AssertionError(
                String.format("JWT with valid signature and not expired should always be accepted. " +
                    "Subject: %s, Roles: %s", subject, roles)
            );
        }
        
        // Verify the JWT is not expired
        if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(Instant.now())) {
            throw new AssertionError("Valid JWT should not be expired");
        }
    }
    
    /**
     * Property: Invalid signature always results in rejection.
     */
    @Property(tries = 100)
    void invalidSignatureAlwaysRejected(
        @ForAll("subjects") String subject,
        @ForAll("roles") List<String> roles,
        @ForAll boolean expired
    ) throws Exception {
        String token = createJwtWithInvalidSignature(subject, roles, expired);
        
        boolean accepted = false;
        try {
            Jwt jwt = jwtDecoder.decode(token).block();
            accepted = (jwt != null);
        } catch (Exception e) {
            accepted = false;
        }
        
        if (accepted) {
            throw new AssertionError(
                String.format("JWT with invalid signature should always be rejected. " +
                    "Subject: %s, Roles: %s, Expired: %b", subject, roles, expired)
            );
        }
    }
    
    /**
     * Property: Expired JWT always results in rejection (even with valid signature).
     */
    @Property(tries = 100)
    void expiredJwtAlwaysRejected(
        @ForAll("subjects") String subject,
        @ForAll("roles") List<String> roles
    ) throws Exception {
        String token = createExpiredToken(subject, roles);
        
        boolean accepted = false;
        try {
            Jwt jwt = jwtDecoder.decode(token).block();
            accepted = (jwt != null);
        } catch (Exception e) {
            accepted = false;
        }
        
        if (accepted) {
            throw new AssertionError(
                String.format("Expired JWT should always be rejected. " +
                    "Subject: %s, Roles: %s", subject, roles)
            );
        }
    }
    
    // Arbitraries for generating test data
    
    @Provide
    Arbitrary<String> subjects() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<List<String>> roles() {
        return Arbitraries.of("ROLE_CONSUMER", "ROLE_RESTAURANT", "ROLE_COURIER", "ROLE_ADMIN")
            .list()
            .ofMinSize(1)
            .ofMaxSize(3);
    }
    
    // Helper methods for creating JWTs
    
    private String createJwt(String subject, boolean validSignature, boolean notExpired) throws Exception {
        Instant now = Instant.now();
        Instant expiration = notExpired 
            ? now.plus(1, ChronoUnit.HOURS)
            : now.minus(1, ChronoUnit.HOURS);
        
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("test-issuer")
            .expirationTime(Date.from(expiration))
            .issueTime(Date.from(now))
            .claim("roles", List.of("ROLE_CONSUMER"))
            .build();
        
        SignedJWT signedJWT = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        );
        
        RSAPrivateKey keyToUse = validSignature ? validPrivateKey : invalidPrivateKey;
        signedJWT.sign(new RSASSASigner(keyToUse));
        return signedJWT.serialize();
    }
    
    private String createValidToken(String subject, List<String> roles) throws Exception {
        Instant now = Instant.now();
        Instant expiration = now.plus(1, ChronoUnit.HOURS);
        
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("test-issuer")
            .expirationTime(Date.from(expiration))
            .issueTime(Date.from(now))
            .claim("roles", roles)
            .build();
        
        SignedJWT signedJWT = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        );
        
        signedJWT.sign(new RSASSASigner(validPrivateKey));
        return signedJWT.serialize();
    }
    
    private String createExpiredToken(String subject, List<String> roles) throws Exception {
        Instant now = Instant.now();
        Instant expiration = now.minus(1, ChronoUnit.HOURS);
        
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("test-issuer")
            .expirationTime(Date.from(expiration))
            .issueTime(Date.from(now.minus(2, ChronoUnit.HOURS)))
            .claim("roles", roles)
            .build();
        
        SignedJWT signedJWT = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        );
        
        signedJWT.sign(new RSASSASigner(validPrivateKey));
        return signedJWT.serialize();
    }
    
    private String createJwtWithInvalidSignature(String subject, List<String> roles, boolean expired) throws Exception {
        Instant now = Instant.now();
        Instant expiration = expired 
            ? now.minus(1, ChronoUnit.HOURS)
            : now.plus(1, ChronoUnit.HOURS);
        
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("test-issuer")
            .expirationTime(Date.from(expiration))
            .issueTime(Date.from(now))
            .claim("roles", roles)
            .build();
        
        SignedJWT signedJWT = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        );
        
        signedJWT.sign(new RSASSASigner(invalidPrivateKey));
        return signedJWT.serialize();
    }
}
