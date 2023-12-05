package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;

import java.util.Map;

public interface OpenTelemetryConfiguration {
  Map<String, CustomMetricInstrumentDefinition> getMetricInstrumentDefinitionMap();

  boolean isTurnOffTracing();

  boolean isTurnOffMetrics();

  TraceLevelConfiguration getTraceLevelConfiguration();

  ExporterConfiguration getExporterConfiguration();

  SpanProcessorConfiguration getSpanProcessorConfiguration();

  OpenTelemetryResource getResource();

  String getConfigName();
}
