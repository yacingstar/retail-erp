package com.retailerp.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.jwt.*} properties to a typed object.
 * {@code @ConfigurationProperties} + a record = immutable, validated-at-startup configuration.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
}
