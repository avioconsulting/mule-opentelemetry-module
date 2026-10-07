package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.AuthenticationValidation;
import com.avioconsulting.mule.opentelemetry.internal.auth.ExporterAuthenticationProvider;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection-scoped adaptation of standard OTLP exporters using public SDK APIs
 * only.
 */
public final class OtlpAuthenticationCustomizer {
  private final ExporterAuthenticationProvider provider;
  private final AtomicInteger authenticatedSignals = new AtomicInteger();

  public OtlpAuthenticationCustomizer(ExporterAuthenticationProvider provider) {
    this.provider = provider;
  }

  public void register(AutoConfiguredOpenTelemetrySdkBuilder builder) {
    builder.addSpanExporterCustomizer(this::spans);
    builder.addMetricExporterCustomizer(this::metrics);
    builder.addLogRecordExporterCustomizer(this::logs);
  }

  public boolean isUsed() {
    return authenticatedSignals.get() > 0;
  }

  SpanExporter spans(SpanExporter original, ConfigProperties properties) {
    if (!(original instanceof OtlpGrpcSpanExporter) && !(original instanceof OtlpHttpSpanExporter)) {
      return original;
    }
    validate(properties, "traces");
    SpanExporter replacement = original instanceof OtlpGrpcSpanExporter
        ? ((OtlpGrpcSpanExporter) original).toBuilder().setHeaders(provider::headers).build()
        : ((OtlpHttpSpanExporter) original).toBuilder().setHeaders(provider::headers).build();
    try {
      original.shutdown();
    } catch (RuntimeException failure) {
      replacement.shutdown();
      throw failure;
    }
    authenticatedSignals.incrementAndGet();
    return replacement;
  }

  MetricExporter metrics(MetricExporter original, ConfigProperties properties) {
    if (!(original instanceof OtlpGrpcMetricExporter) && !(original instanceof OtlpHttpMetricExporter)) {
      return original;
    }
    validate(properties, "metrics");
    MetricExporter replacement = original instanceof OtlpGrpcMetricExporter
        ? ((OtlpGrpcMetricExporter) original).toBuilder().setHeaders(provider::headers)
            .setAggregationTemporalitySelector(original::getAggregationTemporality)
            .setDefaultAggregationSelector(original::getDefaultAggregation)
            .setMemoryMode(original.getMemoryMode()).build()
        : ((OtlpHttpMetricExporter) original).toBuilder().setHeaders(provider::headers)
            .setAggregationTemporalitySelector(original::getAggregationTemporality)
            .setDefaultAggregationSelector(original::getDefaultAggregation)
            .setMemoryMode(original.getMemoryMode()).build();
    try {
      original.shutdown();
    } catch (RuntimeException failure) {
      replacement.shutdown();
      throw failure;
    }
    authenticatedSignals.incrementAndGet();
    return replacement;
  }

  LogRecordExporter logs(LogRecordExporter original, ConfigProperties properties) {
    if (!(original instanceof OtlpGrpcLogRecordExporter) && !(original instanceof OtlpHttpLogRecordExporter)) {
      return original;
    }
    validate(properties, "logs");
    LogRecordExporter replacement = original instanceof OtlpGrpcLogRecordExporter
        ? ((OtlpGrpcLogRecordExporter) original).toBuilder().setHeaders(provider::headers).build()
        : ((OtlpHttpLogRecordExporter) original).toBuilder().setHeaders(provider::headers).build();
    try {
      original.shutdown();
    } catch (RuntimeException failure) {
      replacement.shutdown();
      throw failure;
    }
    authenticatedSignals.incrementAndGet();
    return replacement;
  }

  private void validate(ConfigProperties properties, String signal) {
    String prefix = "otel.exporter.otlp.";
    String endpoint = properties.getString(prefix + signal + ".endpoint");
    if (endpoint == null) {
      endpoint = properties.getString(prefix + "endpoint");
    }
    AuthenticationValidation.httpsUrl(endpoint, "collectorEndpoint");
    String headersProperty = properties.getString(prefix + signal + ".headers") != null
        ? prefix + signal + ".headers"
        : prefix + "headers";
    for (String header : properties.getMap(headersProperty).keySet()) {
      if (header.equalsIgnoreCase(provider.getHeaderName())) {
        throw new IllegalArgumentException("Exporter authentication conflicts with configured OTLP headers");
      }
    }
  }
}
