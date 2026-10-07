package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared per-connection export gate; authentication failures stay
 * telemetry-local.
 */
public final class AuthenticationExportGate {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationExportGate.class);
  private final ExporterAuthenticationProvider provider;
  private boolean warned;
  private long lastWarning;

  public AuthenticationExportGate(ExporterAuthenticationProvider provider) {
    this.provider = provider;
  }

  public CompletableResultCode export(Supplier<CompletableResultCode> delegate) {
    try {
      provider.ensureUsable();
      return delegate.get();
    } catch (OAuth2AuthenticationException failure) {
      warn(failure);
      return CompletableResultCode.ofFailure();
    }
  }

  private synchronized void warn(OAuth2AuthenticationException failure) {
    long now = System.nanoTime();
    if (!warned || now - lastWarning >= TimeUnit.SECONDS.toNanos(30)) {
      // Advance before logging: an appender-triggered reentrant export cannot log
      // again.
      warned = true;
      lastWarning = now;
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("OTLP export skipped because authentication is unavailable ({})", failure.getCategory());
      }
    }
  }
}
