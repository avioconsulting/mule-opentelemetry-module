package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import com.avioconsulting.mule.opentelemetry.internal.util.logger.PeriodicLogger;
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
  private final PeriodicLogger warningLogger;

  public AuthenticationExportGate(ExporterAuthenticationProvider provider) {
    this(provider, LOGGER, System::nanoTime);
  }

  AuthenticationExportGate(ExporterAuthenticationProvider provider, Logger logger, LongSupplier clock) {
    this.provider = provider;
    this.logger = logger;
    this.warningLogger = PeriodicLogger.builder().interval(30, TimeUnit.SECONDS)
        .includeSuppressionCount(false)
        .clockMillis(() -> TimeUnit.NANOSECONDS.toMillis(clock.getAsLong())).build();
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

  private void warn(OAuth2AuthenticationException failure) {
    // PeriodicLogger advances its timestamp before logging, preventing an
    // appender-triggered reentrant export from repeating this warning.
    warningLogger.warn(logger, "OTLP export skipped because authentication is unavailable "
        + "(state={}, failure={}, reason={}, httpStatus={})", failure.getCategory(),
        failure.getFailureCategory(), failure.getReason(), failure.getHttpStatus());
  }
}
