package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;

public interface OpenTelemetryConfiguration {

  boolean isTurnOffTracing();

  TraceLevelConfiguration getTraceLevelConfiguration();

  ExporterConfiguration getExporterConfiguration();

  SpanProcessorConfiguration getSpanProcessorConfiguration();

  OpenTelemetryResource getResource();

  String getConfigName();
}
