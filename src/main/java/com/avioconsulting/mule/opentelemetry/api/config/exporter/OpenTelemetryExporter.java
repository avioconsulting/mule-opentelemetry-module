package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import java.util.Map;

public interface OpenTelemetryExporter {
  String OTEL_TRACES_EXPORTER_KEY = "otel.traces.exporter";
  String OTEL_METRICS_EXPORTER_KEY = "otel.metrics.exporter";
  String OTEL_LOGS_EXPORTER_KEY = "otel.logs.exporter";

  Map<String, String> getExporterProperties();

}
