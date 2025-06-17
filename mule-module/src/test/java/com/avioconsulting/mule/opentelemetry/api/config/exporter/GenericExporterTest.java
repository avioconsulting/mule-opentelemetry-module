package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import org.junit.Test;

import java.util.Collections;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter.*;
import static org.assertj.core.api.Assertions.assertThat;

public class GenericExporterTest {

  @Test
  public void testGenericExporter_WithNoConfigProperties() {
    GenericExporter genericExporter = new GenericExporter();
    assertThat(genericExporter.getExporterProperties())
        .containsEntry(OTEL_TRACES_EXPORTER_KEY, "none")
        .containsEntry(OTEL_METRICS_EXPORTER_KEY, "none")
        .containsEntry(OTEL_LOGS_EXPORTER_KEY, "none");
  }

  @Test
  public void testGenericExporter_WithConfigProperties() {
    GenericExporter genericExporter = new GenericExporter();
    genericExporter.setConfigProperties(Collections.singletonMap(OTEL_TRACES_EXPORTER_KEY, "zipkin"));
    assertThat(genericExporter.getExporterProperties())
        .containsEntry(OTEL_TRACES_EXPORTER_KEY, "zipkin")
        .containsEntry(OTEL_METRICS_EXPORTER_KEY, "none")
        .containsEntry(OTEL_LOGS_EXPORTER_KEY, "none");
  }
}