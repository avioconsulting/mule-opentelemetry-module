package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.AuthenticationValidation;
import java.util.concurrent.TimeUnit;

/** Opaque access token; deliberately does not render its value. */
public final class OAuth2AccessToken {
  private final String value;
  private final long lifetimeNanos;

  public OAuth2AccessToken(String value, long lifetimeSeconds) {
    AuthenticationValidation.headerValue(value);
    if (lifetimeSeconds <= 0 || lifetimeSeconds > Long.MAX_VALUE / TimeUnit.SECONDS.toNanos(1) / 2) {
      throw OAuth2AuthenticationException.invalidResponse(OAuth2AuthenticationException.Reason.EXPIRES_IN);
    }
    this.value = value;
    this.lifetimeNanos = TimeUnit.SECONDS.toNanos(lifetimeSeconds);
  }

  public String getValue() {
    return value;
  }

  public long getLifetimeNanos() {
    return lifetimeNanos;
  }
}
