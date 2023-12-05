package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.metrics;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.oshi.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;

public class OSMetrics {
  public static void installMetrics(OpenTelemetryConnection openTelemetryConnection) {
    openTelemetryConnection.registerMetricsObserver(SystemMetrics::registerObservers);
    openTelemetryConnection.registerMetricsObserver(ProcessMetrics::registerObservers);
  }
}
