package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.Collections;
import java.util.Map;

public class LoggingExporter extends AbstractExporter {

  public static final String OTEL_EXPORTER_LOGGING_PREFIX_KEY = "otel.exporter.logging.prefix";
  @Parameter
  @Optional
  @Summary("An optional string printed in front of the span name and attributes")
  private String logPrefix;

  public String getLogPrefix() {
    return logPrefix;
  }

  @Override
  public Map<String, String> getExporterProperties() {
    Map<String, String> config = super.getExporterProperties();
    config.put(OpenTelemetryExporter.OTEL_TRACES_EXPORTER_KEY, "logging");
    config.put(OTEL_EXPORTER_LOGGING_PREFIX_KEY, getLogPrefix());
    return Collections.unmodifiableMap(config);
  }
}
