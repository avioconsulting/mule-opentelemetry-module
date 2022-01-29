package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import org.junit.Test;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter.OTEL_EXPORTER_LOGGING_PREFIX_KEY;
import static com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter.OTEL_LOGS_EXPORTER_KEY;
import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter.OTEL_METRICS_EXPORTER_KEY;
import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter.OTEL_TRACES_EXPORTER_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public class LoggingExporterTest {

  @Test
  public void getExporterProperties() {
    LoggingExporter exporter = new LoggingExporter();
    assertThat(exporter.getExporterProperties())
        .containsEntry(OTEL_TRACES_EXPORTER_KEY, "logging")
        .containsEntry(OTEL_METRICS_EXPORTER_KEY, "none")
        .containsEntry(OTEL_LOGS_EXPORTER_KEY, "none")
        .containsEntry(OTEL_EXPORTER_LOGGING_PREFIX_KEY, null);
  }
}