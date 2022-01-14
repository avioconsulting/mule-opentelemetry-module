package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.KeyValuePair;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LoggingExporter implements OpenTelemetryExporter {

  @Parameter
  @Optional
  @Summary("An optional string printed in front of the span name and attributes")
  private String logPrefix;

  public String getLogPrefix() {
    return logPrefix;
  }

  @Override
  public Map<String, String> getConfigProperties() {
    Map<String, String> config = new HashMap<>();
    config.put("otel.traces.exporter", "logging");
    config.put("otel.metrics.exporter", "logging");
    config.put("otel.logs.exporter", "logging");
    config.put("otel.exporter.logging.prefix", getLogPrefix());
    return Collections.unmodifiableMap(config);
  }
}
