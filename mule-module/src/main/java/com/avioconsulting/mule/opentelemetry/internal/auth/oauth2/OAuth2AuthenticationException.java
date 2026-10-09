package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

/** Sanitized failure. Never retains HTTP response text or a sensitive cause. */
public final class OAuth2AuthenticationException extends RuntimeException {
  public enum Category {
    TRANSPORT, HTTP, TIMEOUT, INVALID_RESPONSE, INTERRUPTED, CLOSED, COOLDOWN
  }

  /**
   * Fixed diagnostic vocabulary; never derived from exception messages or
   * response text.
   */
  public enum Reason {
    UNSPECIFIED, TLS, DNS, CONNECTION, MALFORMED_JSON, RESPONSE_TOO_LARGE, ACCESS_TOKEN, TOKEN_TYPE, EXPIRES_IN
  }

  private final Category category;
  private final Category failureCategory;
  private final Reason reason;
  private final int httpStatus;

  public OAuth2AuthenticationException(Category category) {
    this(category, category, Reason.UNSPECIFIED, 0);
  }

  private OAuth2AuthenticationException(Category category, Category failureCategory, Reason reason, int httpStatus) {
    super("Exporter authentication unavailable: " + category.name() + " (failure=" + failureCategory.name()
        + ", reason=" + reason.name() + ", httpStatus=" + httpStatus + ")");
    this.category = category;
    this.failureCategory = failureCategory;
    this.reason = reason;
    this.httpStatus = httpStatus;
  }

  public static OAuth2AuthenticationException http(int status) {
    return new OAuth2AuthenticationException(Category.HTTP, Category.HTTP, Reason.UNSPECIFIED, status);
  }

  public static OAuth2AuthenticationException invalidResponse(Reason reason) {
    return new OAuth2AuthenticationException(Category.INVALID_RESPONSE, Category.INVALID_RESPONSE, reason, 0);
  }

  public static OAuth2AuthenticationException transport(Throwable failure) {
    // Bound traversal and retain only allowlisted classifications, never the cause
    // itself.
    for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
      if (failure instanceof OAuth2AuthenticationException) {
        OAuth2AuthenticationException sanitized = (OAuth2AuthenticationException) failure;
        // A try-with-resources close may attach a sensitive suppressed exception.
        return new OAuth2AuthenticationException(sanitized.category, sanitized.failureCategory,
            sanitized.reason, sanitized.httpStatus);
      }
      if (failure instanceof javax.net.ssl.SSLException) {
        return new OAuth2AuthenticationException(Category.TRANSPORT, Category.TRANSPORT, Reason.TLS, 0);
      }
      if (failure instanceof java.net.UnknownHostException) {
        return new OAuth2AuthenticationException(Category.TRANSPORT, Category.TRANSPORT, Reason.DNS, 0);
      }
      if (failure instanceof java.net.ConnectException) {
        return new OAuth2AuthenticationException(Category.TRANSPORT, Category.TRANSPORT, Reason.CONNECTION, 0);
      }
      if (failure instanceof java.net.SocketTimeoutException
          || failure instanceof java.util.concurrent.TimeoutException) {
        return new OAuth2AuthenticationException(Category.TIMEOUT);
      }
    }
    return new OAuth2AuthenticationException(Category.TRANSPORT);
  }

  public static OAuth2AuthenticationException cooldown(OAuth2AuthenticationException failure) {
    return new OAuth2AuthenticationException(Category.COOLDOWN, failure.failureCategory, failure.reason,
        failure.httpStatus);
  }

  public Category getCategory() {
    return category;
  }

  public Category getFailureCategory() {
    return failureCategory;
  }

  public Reason getReason() {
    return reason;
  }

  /** @return HTTP status, or zero when no HTTP error response was received */
  public int getHttpStatus() {
    return httpStatus;
  }
}
