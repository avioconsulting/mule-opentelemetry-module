package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Used during tests. This is not configured in module service loader provider,
 * so cannot be used by module.
 *
 * This stores them
 * in {@link DelegatedLoggingSpanTestExporter#spanQueue} for tests to access and
 * verify.
 */
public class DelegatedLoggingSpanTestExporterProvider implements ConfigurableSpanExporterProvider {
  @Override
  public SpanExporter createExporter(ConfigProperties config) {
    return new DelegatedLoggingSpanTestExporter(config);
  }

  @Override
  public String getName() {
    return "delegatedLogging";
  }

}