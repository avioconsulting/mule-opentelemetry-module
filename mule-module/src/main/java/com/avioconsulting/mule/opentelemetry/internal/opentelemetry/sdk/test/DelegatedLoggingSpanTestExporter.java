package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DelegatedLoggingSpanTestExporter implements SpanExporter {
  public static final Queue<Span> spanQueue = new ConcurrentLinkedQueue<>();
  private final ConfigProperties config;
  private static final Logger logger = Logger.getLogger(DelegatedLoggingSpanTestExporter.class.getName());

  public DelegatedLoggingSpanTestExporter(ConfigProperties config) {
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
      span.setSpanStatusDescription(spanData.getStatus().getDescription());
      span.setInstrumentationName(spanData.getInstrumentationScopeInfo().getName());
      span.setInstrumentationVersion(spanData.getInstrumentationScopeInfo().getVersion());
      span.setStartEpocNanos(spanData.getStartEpochNanos());
      span.setEndEpocNanos(spanData.getEndEpochNanos());
      Map<String, Object> attributes = new HashMap<>();
      spanData.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), value));
      span.setAttributes(attributes);
      span.setSpanContext(new SpanContext(spanData.getSpanContext()));
      span.setParentSpanContext(new SpanContext(spanData.getParentSpanContext()));
      logger.log(Level.INFO, span.toString());
      return span;
    }).forEach(spanQueue::add);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    spanQueue.clear();
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return flush();
  }

  public ConfigProperties getConfig() {
    return config;
  }

  public static class Span {
    private String instrumentationName;
    private String instrumentationVersion;
    private String spanName;
    private String traceId;
    private String spanId;
    private String spanKind;
    private String spanStatus;
    private Map<String, Object> attributes;

    private SpanContext parentSpanContext;
    private SpanContext spanContext;
    private String spanStatusDescription;
    private long startEpocNanos;
    private long endEpocNanos;

    public String getInstrumentationName() {
      return instrumentationName;
    }

    public void setInstrumentationName(String instrumentationName) {
      this.instrumentationName = instrumentationName;
    }

    public String getInstrumentationVersion() {
      return instrumentationVersion;
    }

    public void setInstrumentationVersion(String instrumentationVersion) {
      this.instrumentationVersion = instrumentationVersion;
    }

    public String getSpanStatus() {
      return spanStatus;
    }

    public void setSpanStatus(String spanStatus) {
      this.spanStatus = spanStatus;
    }

    public String getSpanStatusDescription() {
      return spanStatusDescription;
    }

    public Span setSpanStatusDescription(String spanStatusDescription) {
      this.spanStatusDescription = spanStatusDescription;
      return this;
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

    public SpanContext getParentSpanContext() {
      return parentSpanContext;
    }

    public void setParentSpanContext(SpanContext parentSpanContext) {
      this.parentSpanContext = parentSpanContext;
    }

    public SpanContext getSpanContext() {
      return spanContext;
    }

    public void setSpanContext(SpanContext spanContext) {
      this.spanContext = spanContext;
    }

    @Override
    public String toString() {
      return "Span{" +
          "instrumentationName='" + instrumentationName + '\'' +
          ", instrumentationVersion='" + instrumentationVersion + '\'' +
          ", spanName='" + spanName + '\'' +
          ", traceId='" + traceId + '\'' +
          ", spanId='" + spanId + '\'' +
          ", spanKind='" + spanKind + '\'' +
          ", spanStatus='" + spanStatus + '\'' +
          ", attributes=" + attributes +
          ", parentSpanContext=" + parentSpanContext +
          ", spanContext=" + spanContext +
          ", spanStatusDescription='" + spanStatusDescription + '\'' +
          ", startEpocNanos=" + startEpocNanos +
          ", endEpocNanos=" + endEpocNanos +
          '}';
    }

    public void setStartEpocNanos(long startEpocNanos) {
      this.startEpocNanos = startEpocNanos;
    }

    public long getStartEpocNanos() {
      return startEpocNanos;
    }

    public void setEndEpocNanos(long endEpocNanos) {
      this.endEpocNanos = endEpocNanos;
    }

    public long getEndEpocNanos() {
      return endEpocNanos;
    }
  }

  public static class SpanContext {
    private final String traceId;
    private final String spanId;

    public SpanContext(io.opentelemetry.api.trace.SpanContext spanContext) {
      this.spanId = spanContext.getSpanId();
      this.traceId = spanContext.getTraceId();
    }

    public String getTraceId() {
      return traceId;
    }

    public String getSpanId() {
      return spanId;
    }

    @Override
    public String toString() {
      return "SpanContext{" +
          "traceId='" + traceId + '\'' +
          ", spanId='" + spanId + '\'' +
          '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      SpanContext that = (SpanContext) o;
      return Objects.equals(getTraceId(), that.getTraceId()) && Objects.equals(getSpanId(), that.getSpanId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getTraceId(), getSpanId());
    }
  }
}
