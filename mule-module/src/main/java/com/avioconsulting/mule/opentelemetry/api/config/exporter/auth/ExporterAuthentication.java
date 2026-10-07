package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

/** Connection-local authentication for standard OTLP exporters. */
public interface ExporterAuthentication {
  /** Validate configuration without including credential values in errors. */
  void validate();

  /** @return the HTTP header owned exclusively by this authentication */
  String getHeaderName();
}
