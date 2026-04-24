package net.ftgo.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT validation logic.
 * Tests that JWT is accepted iff signature is valid AND not expired.
 */
class JwtValidationUnitTest {
    
    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;
    private static ReactiveJwtDecoder jwtDecoder;
    
    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        
        jwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
    }
    
    @Test
    void testValidJwtIsAccepted() throws Exception {
        String token = createValidToken("user123", List.of("ROLE_CONSUMER"));
        
        Jwt jwt = jwtDecoder.decode(token).block();
        
        assertNotNull(jwt);
        assertEquals("user123", jwt.getSubject());
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }
    
    @Test
    void testExpiredJwtIsRejected() throws Exception {
        String token = createExpiredToken("user123", List.of("ROLE_CONSUMER"));
        
        // Expired JWT should throw JwtValidationException
        assertThrows(Exception.class, () -> jwtDecoder.decode(token).block(),
            "Expired JWT should be rejected");
    }
    
    @Test
    void testInvalidSignatureIsRejected() throws Exception {
        // Create token with different key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair differentKeyPair = keyPairGenerator.generateKeyPair();
        RSAPrivateKey differentPrivateKey = (RSAPrivateKey) differentKeyPair.getPrivate();
        
        String token = createTokenWithKey("user123", List.of("ROLE_CONSUMER"), differentPrivateKey);
        
        assertThrows(Exception.class, () -> jwtDecoder.decode(token).block(),
            "JWT with invalid signature should be rejected");
    }
    
    @Test
    void testJwtWithValidSignatureAndNotExpiredIsAccepted() throws Exception {
        String token = createValidToken("user123", List.of("ROLE_CONSUMER"));
        
        Jwt jwt = jwtDecoder.decode(token).block();
        
        assertNotNull(jwt);
        // Valid signature (no exception thrown)
        // Not expired
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }
    
    @Test
    void testRolesAreExtractedFromJwt() throws Exception {
        String token = createValidToken("user123", List.of("ROLE_CONSUMER", "ROLE_ADMIN"));
        
        Jwt jwt = jwtDecoder.decode(token).block();
        
        assertNotNull(jwt);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) jwt.getClaim("roles");
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_CONSUMER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
    }
    
    /**
     * Create a valid JWT token with the test private key.
     */
    private String createValidToken(String subject, List<String> roles) throws Exception {
        return createTokenWithKey(subject, roles, privateKey);
    }
    
    /**
     * Create an expired JWT token.
     */
    private String createExpiredToken(String subject, List<String> roles) throws Exception {
        Instant now = Instant.now();
        Instant expiration = now.minus(1, ChronoUnit.HOURS); // Expired 1 hour ago
        
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
        
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }
    
    /**
     * Create a JWT token with a specific private key.
     */
    private String createTokenWithKey(String subject, List<String> roles, RSAPrivateKey key) throws Exception {
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
        
        signedJWT.sign(new RSASSASigner(key));
        return signedJWT.serialize();
    }
}
