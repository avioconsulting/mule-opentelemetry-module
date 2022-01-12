package com.avioconsulting.mule.opentelemetry.internal;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryConnectionProvider
    implements PoolingConnectionProvider<OpenTelemetryConnection> {

  private final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryConnectionProvider.class);

  @Override
  public OpenTelemetryConnection connect() throws ConnectionException {
    return new OpenTelemetryConnection("");
  }

  @Override
  public void disconnect(OpenTelemetryConnection connection) {
    try {
      connection.invalidate();
    } catch (Exception e) {
      LOGGER.error(
          "Error while disconnecting [" + connection.getId() + "]: " + e.getMessage(), e);
    }
  }

  @Override
  public ConnectionValidationResult validate(OpenTelemetryConnection connection) {
    return ConnectionValidationResult.success();
  }
}
