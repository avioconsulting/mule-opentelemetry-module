package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;

import java.util.Map;

public class OpenTelemetryOperations {

  @DisplayName("Get Trace Context")
  @Alias("get-trace-context")
  public Map<String, String> getTraceContext(@Connection OpenTelemetryConnection openTelemetryConnection,
      CorrelationInfo correlationInfo) {
    String transactionId = correlationInfo.getCorrelationId();
    return openTelemetryConnection.getTraceContext(transactionId);
  }
}
