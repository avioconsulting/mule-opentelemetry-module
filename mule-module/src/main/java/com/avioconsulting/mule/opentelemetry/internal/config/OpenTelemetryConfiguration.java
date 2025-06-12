package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.http.api.HttpService;

public interface OpenTelemetryConfiguration {

  boolean isTurnOffTracing();

  boolean isTurnOffMetrics();

  TraceLevelConfiguration getTraceLevelConfiguration();

  ExporterConfiguration getExporterConfiguration();

  SpanProcessorConfiguration getSpanProcessorConfiguration();

  OpenTelemetryResource getResource();

  String getConfigName();

  HttpService getHttpService();

  AppIdentifier getAppIdentifier();

  OpenTelemetryMetricsConfigProvider getMetricsConfigProvider();

  ExpressionManager getExpressionManager();
}
