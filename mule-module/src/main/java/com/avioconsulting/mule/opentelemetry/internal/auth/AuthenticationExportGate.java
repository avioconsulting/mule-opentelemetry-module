package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/**
 * Shared per-connection export gate; authentication failures stay
 * telemetry-local.
 */
public final class AuthenticationExportGate {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationExportGate.class);
  private final ExporterAuthenticationProvider provider;
  private final Logger logger;
  private final LongSupplier clock;
  private boolean warned;
  private long lastWarning;

  public AuthenticationExportGate(ExporterAuthenticationProvider provider) {
    this(provider, LOGGER, System::nanoTime);
  }

  AuthenticationExportGate(ExporterAuthenticationProvider provider, Logger logger, LongSupplier clock) {
    this.provider = provider;
    this.logger = logger;
    this.clock = clock;
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
    long now = clock.getAsLong();
    if (!warned || now - lastWarning >= TimeUnit.SECONDS.toNanos(30)) {
      // Advance before logging: an appender-triggered reentrant export cannot log
      // again.
      warned = true;
      lastWarning = now;
      if (logger.isWarnEnabled()) {
        logger.warn("OTLP export skipped because authentication is unavailable "
            + "(state={}, failure={}, reason={}, httpStatus={})", failure.getCategory(),
            failure.getFailureCategory(), failure.getReason(), failure.getHttpStatus());
      }
    }
  }
}
