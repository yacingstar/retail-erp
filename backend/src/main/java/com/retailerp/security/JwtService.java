package com.retailerp.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.retailerp.user.Role;

/**
 * Creates and verifies JSON Web Tokens signed with HMAC-SHA256 (one shared secret key).
 * <p>
 * A JWT is {@code header.payload.signature}, each part Base64URL-encoded. The payload ("claims") is readable by
 * anyone; the signature only proves WE issued it and nobody changed it. So: never put secrets in claims.
 */
@Service
public class JwtService {

    public static final String CLAIM_TENANT = "tenant_id";
    public static final String CLAIM_ROLES = "roles";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(loadKey(properties.secret()), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.userId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_TENANT, user.tenantId().toString())
                .claim(CLAIM_ROLES, List.of(user.role().name()))
                .claim("email", user.email())
                .claim("name", user.fullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /** Token for a platform administrator: no tenant_id claim, role PLATFORM_ADMIN. */
    public IssuedToken issuePlatform(java.util.UUID adminId, String email, String fullName) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(adminId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_ROLES, List.of("PLATFORM_ADMIN"))
                .claim("email", email)
                .claim("name", fullName)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static byte[] loadKey(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            log.warn("app.jwt.secret (JWT_SECRET) is not set: using a random key. Tokens become invalid on restart. "
                    + "Set JWT_SECRET for anything but local development.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] key = Base64.getDecoder().decode(base64Secret.trim());
        if (key.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256; got " + key.length);
        }
        return key;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** What we know about a logged-in user when issuing a token. */
    public record AuthenticatedUser(java.util.UUID userId, java.util.UUID tenantId, String email, String fullName, Role role) {
    }
}
