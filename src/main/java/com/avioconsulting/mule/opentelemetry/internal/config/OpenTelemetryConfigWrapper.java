package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;

public class OpenTelemetryConfigWrapper {
  private final OpenTelemetryResource resource;
  private final OpenTelemetryExporter exporter;

  public OpenTelemetryConfigWrapper(OpenTelemetryResource resource, OpenTelemetryExporter exporter) {
    this.resource = resource;
    this.exporter = exporter;
  }

  public OpenTelemetryResource getResource() {
    return resource;
  }

  public OpenTelemetryExporter getExporter() {
    return exporter;
  }
}
