package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.HashMap;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter.*;

public abstract class AbstractExporter implements OpenTelemetryExporter {

  @Parameter
  @NullSafe
  @Optional
  @Summary("Additional Configuration properties for Exporter. System or Environment Variables will override this configuration.")
  private Map<String, String> configProperties = new HashMap<>();

  public Map<String, String> getConfigProperties() {
    return configProperties;
  }

  public void setConfigProperties(Map<String, String> configProperties) {
    this.configProperties = configProperties;
  }

  @Override
  public Map<String, String> getExporterProperties() {
    Map<String, String> config = new HashMap<>();
    config.put(OTEL_TRACES_EXPORTER_KEY, "none");
    config.put(OTEL_METRICS_EXPORTER_KEY, "none");
    config.put(OTEL_LOGS_EXPORTER_KEY, "none");
    config.putAll(getConfigProperties());
    return config;
  }
}
