package com.avioconsulting.mule.opentelemetry.internal.connection;

import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class OpenTelemetryConnectionProvider
    implements CachedConnectionProvider<OpenTelemetryConnection> {

  private final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryConnectionProvider.class);

  public static final String INSTRUMENTATION_VERSION = "0.0.1";
  public static final String INSTRUMENTATION_NAME = "com.avioconsulting.mule.tracing";

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Override
  public OpenTelemetryConnection connect() throws ConnectionException {
    return OpenTelemetryConnection.get().orElseThrow(
        () -> new ConnectionException("Configuration must fist start for OpenTelemetry connection."));
  }

  @Override
  public void disconnect(OpenTelemetryConnection connection) {
    try {
      connection.invalidate();
    } catch (Exception e) {
      LOGGER.error(
          "Error while disconnecting OpenTelemetry: " + e.getMessage(), e);
    }
  }

  @Override
  public ConnectionValidationResult validate(OpenTelemetryConnection connection) {
    return ConnectionValidationResult.success();
  }
}
