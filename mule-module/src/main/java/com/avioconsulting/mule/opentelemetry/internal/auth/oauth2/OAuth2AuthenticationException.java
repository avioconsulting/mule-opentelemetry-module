package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

/** Sanitized failure. Never retains HTTP response text or a sensitive cause. */
public final class OAuth2AuthenticationException extends RuntimeException {
  public enum Category {
    TRANSPORT, HTTP, TIMEOUT, INVALID_RESPONSE, INTERRUPTED, CLOSED, COOLDOWN
  }

  private final Category category;

  public OAuth2AuthenticationException(Category category) {
    super("Exporter authentication unavailable: " + category.name());
    this.category = category;
  }

  public Category getCategory() {
    return category;
  }
}
