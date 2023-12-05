package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

/**
 * Used during tests. This is not configured in module service loader provider,
 * so cannot be used by module.
 * This delegates metrics to {@link LoggingSpanExporter} and also stores them
 * in {@link DelegatedLoggingMetricsTestExporter#metricQueue} for tests to
 * access
 * and
 * verify.
 */
public class DelegatedLoggingMetricsTestExporterProvider implements ConfigurableMetricExporterProvider {
  @Override
  public MetricExporter createExporter(ConfigProperties configProperties) {
    return new DelegatedLoggingMetricsTestExporter(configProperties);
  }

  @Override
  public String getName() {
    return "delegatedLogging";
  }

}
