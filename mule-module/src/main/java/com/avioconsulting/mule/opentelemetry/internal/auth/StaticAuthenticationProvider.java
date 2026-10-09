package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.ApiKeyAuth;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.BearerTokenAuth;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.ExporterAuthentication;
import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import java.util.Collections;
import java.util.Map;

/** Static authentication creates no token HTTP client. */
public final class StaticAuthenticationProvider implements ExporterAuthenticationProvider {
  private final String headerName;
  private volatile Map<String, String> credentials;

  public StaticAuthenticationProvider(ExporterAuthentication authentication) {
    authentication.validate();
    headerName = authentication.getHeaderName();
    String value;
    if (authentication instanceof BearerTokenAuth) {
      String token = ((BearerTokenAuth) authentication).getToken();
      if (token.startsWith("Bearer ")) {
        value = token;
      } else {
        value = "Bearer " + token;
      }
    } else if (authentication instanceof ApiKeyAuth) {
      value = ((ApiKeyAuth) authentication).getValue();
    } else {
      throw new IllegalArgumentException("Unsupported static exporter authentication");
    }
    credentials = Collections.singletonMap(headerName, value);
  }

  @Override
  public void ensureUsable() {
    headers();
  }

  @Override
  public Map<String, String> headers() {
    Map<String, String> result = credentials;
    if (result == null) {
      throw new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.CLOSED);
    }
    return result;
  }

  @Override
  public String getHeaderName() {
    return headerName;
  }

  @Override
  public void close() {
    credentials = null;
  }
}
