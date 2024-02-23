package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;

public class OpenTelemetryConfigWrapper {

  private OpenTelemetryConfiguration openTelemetryConfiguration;

  public OpenTelemetryConfigWrapper(OpenTelemetryConfiguration openTelemetryConfiguration) {
    this.openTelemetryConfiguration = openTelemetryConfiguration;
  }

  public OpenTelemetryResource getResource() {
    return openTelemetryConfiguration.getResource();
  }

  public OpenTelemetryExporter getExporter() {
    return openTelemetryConfiguration.getExporterConfiguration().getExporter();
  }

  public SpanProcessorConfiguration getSpanProcessorConfiguration() {
    return openTelemetryConfiguration.getSpanProcessorConfiguration();
  }

  public boolean isTurnOffTracing() {
    return openTelemetryConfiguration.isTurnOffTracing();
  }

  public OpenTelemetryConfiguration getOpenTelemetryConfiguration() {
    return openTelemetryConfiguration;
  }

  public OpenTelemetryConfigWrapper setOpenTelemetryConfiguration(
      OpenTelemetryConfiguration openTelemetryConfiguration) {
    this.openTelemetryConfiguration = openTelemetryConfiguration;
    return this;
  }
}
