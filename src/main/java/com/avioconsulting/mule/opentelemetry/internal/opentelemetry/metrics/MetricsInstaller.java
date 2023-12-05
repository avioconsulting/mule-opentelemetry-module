package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.metrics;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;

public class MetricsInstaller {
  public static void install(OpenTelemetryConnection openTelemetryConnection) {

    Java8RuntimeMetrics.installMetrics(openTelemetryConnection);
    OSMetrics.installMetrics(openTelemetryConnection);
  }
}
