package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import java.net.URI;
import java.net.URISyntaxException;

/** Shared validation; messages contain field names only, never values. */
public final class AuthenticationValidation {
  private AuthenticationValidation() {
  }

  public static void nonblank(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Exporter authentication requires " + field);
    }
  }

  public static void headerName(String name) {
    nonblank(name, "headerName");
    if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || name.endsWith("-bin")) {
      throw new IllegalArgumentException("Invalid exporter authentication headerName");
    }
  }

  public static void headerValue(String value) {
    nonblank(value, "credential");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 32 || c > 126) {
        throw new IllegalArgumentException("Invalid exporter authentication header value");
      }
    }
  }

  public static void httpsUrl(String value, String field) {
    nonblank(value, field);
    try {
      URI uri = new URI(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
        throw new IllegalArgumentException("Exporter authentication requires a secure " + field);
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid exporter authentication " + field);
    }
  }
}
