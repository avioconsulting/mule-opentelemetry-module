package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Used during tests. This is not configured in module service loader provider,
 * so cannot be used by module.
 *
 * This delegates spans to {@link LoggingSpanExporter} and also stores them
 * in {@link DelegatedLoggingSpanExporter#spanQueue} for tests to access and
 * verify.
 */
public class DelegatedLoggingSpanExporterProvider implements ConfigurableSpanExporterProvider {
  @Override
  public SpanExporter createExporter(ConfigProperties config) {
    return new DelegatedLoggingSpanExporter(config);
  }

  @Override
  public String getName() {
    return "delegatedLogging";
  }

  public static class DelegatedLoggingSpanExporter implements SpanExporter {
    private static final LoggingSpanExporter exporter = LoggingSpanExporter.create();
    public static final Queue<Span> spanQueue = new ConcurrentLinkedQueue<>();
    private final ConfigProperties config;

    public DelegatedLoggingSpanExporter(ConfigProperties config) {
      this.config = config;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      spans.stream().map(spanData -> {
        Span span = new Span();
        span.setSpanName(spanData.getName());
        span.setSpanId(spanData.getSpanId());
        span.setSpanKind(spanData.getKind().toString());
        span.setTraceId(spanData.getTraceId());
        span.setSpanStatus(spanData.getStatus().getStatusCode().name());
        Map<String, Object> attributes = new HashMap<>();
        spanData.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), value));
        span.setAttributes(attributes);
        return span;
      }).forEach(spanQueue::add);
      return exporter.export(spans);
    }

    @Override
    public CompletableResultCode flush() {
      spanQueue.clear();
      return exporter.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
      return flush();
    }

    public ConfigProperties getConfig() {
      return config;
    }
  }

  public static class Span {
    private String spanName;
    private String traceId;
    private String spanId;
    private String spanKind;
    private String spanStatus;
    private Map<String, Object> attributes;

    public String getSpanStatus() {
      return spanStatus;
    }

    public void setSpanStatus(String spanStatus) {
      this.spanStatus = spanStatus;
    }

    public void setSpanName(String spanName) {
      this.spanName = spanName;
    }

    public void setTraceId(String traceId) {
      this.traceId = traceId;
    }

    public void setSpanId(String spanId) {
      this.spanId = spanId;
    }

    public void setSpanKind(String spanKind) {
      this.spanKind = spanKind;
    }

    public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
    }

    public String getSpanName() {
      return spanName;
    }

    public String getTraceId() {
      return traceId;
    }

    public String getSpanId() {
      return spanId;
    }

    public String getSpanKind() {
      return spanKind;
    }

    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public String toString() {
      return "Span{" +
          "spanName='" + spanName + '\'' +
          ", traceId='" + traceId + '\'' +
          ", spanId='" + spanId + '\'' +
          ", spanKind='" + spanKind + '\'' +
          ", spanStatus='" + spanStatus + '\'' +
          ", attributes=" + attributes +
          '}';
    }
  }
}