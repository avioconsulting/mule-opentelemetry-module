package com.avioconsulting.mule.opentelemetry.internal.connection;

import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.function.Supplier;

public class OpenTelemetryConnectionProvider
    implements CachedConnectionProvider<Supplier<OpenTelemetryConnection>> {

  private final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryConnectionProvider.class);

  public static final String INSTRUMENTATION_VERSION = "0.0.1";
  public static final String INSTRUMENTATION_NAME = "com.avioconsulting.mule.tracing";

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Override
  public Supplier<OpenTelemetryConnection> connect() throws ConnectionException {
    return OpenTelemetryConnection.supplier();
  }

  @Override
  public void disconnect(Supplier<OpenTelemetryConnection> connection) {
    try {
      OpenTelemetryConnection openTelemetryConnection = connection.get();
      if (openTelemetryConnection != null)
        openTelemetryConnection.invalidate();
    } catch (Exception e) {
      LOGGER.error(
          "Error while disconnecting OpenTelemetry: " + e.getMessage(), e);
    }
  }

  @Override
  public ConnectionValidationResult validate(Supplier<OpenTelemetryConnection> connection) {
    return ConnectionValidationResult.success();
  }
}
