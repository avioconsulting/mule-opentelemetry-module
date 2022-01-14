package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import java.util.Map;

public interface OpenTelemetryExporter {
  Map<String, String> getConfigProperties();
}
