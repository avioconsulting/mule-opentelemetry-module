package com.avioconsulting.mule.opentelemetry.api.config;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * This class represents configuration parameters for OpenTelemetry Exporter to
 * be used.
 */
public class ExporterConfiguration {

  /**
   * Open Telemetry Exporter Configuration. System or Environment Variables will
   * override this configuration. See Documentation for variable details.
   */
  @Parameter
  @DisplayName(value = "OpenTelemetry Exporter")
  @Optional
  @Summary("Open Telemetry Exporter Configuration. System or Environment Variables will override this configuration.")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private OpenTelemetryExporter exporter;

  public OpenTelemetryExporter getExporter() {
    return exporter;
  }
}
