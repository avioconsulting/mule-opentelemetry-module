package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tracing SDK's Span processor configuration parameters.
 */
public class SpanProcessorConfiguration implements OtelConfigMapProvider {

  @Parameter
  @Placement(order = 10, tab = "Tracer Settings")
  @DisplayName("Max Queue Size")
  @Optional(defaultValue = "4096")
  @Summary("The maximum number of spans in the waiting queue. Any new spans are dropped once the queue is full.")
  private long maxQueueSize;

  @Parameter
  @Placement(order = 20, tab = "Tracer Settings")
  @DisplayName("Max Batch Export Size")
  @Optional(defaultValue = "1024")
  @Summary("The maximum number of spans to export in a single batch. This must be smaller or equal to Max Queue Size.")
  private long maxBatchExportSize;

  @Parameter
  @Placement(order = 30, tab = "Tracer Settings")
  @DisplayName("Batch Export Delay Interval")
  @Optional(defaultValue = "1500")
  @Summary("The delay interval in milliseconds between two consecutive batch exports.")
  private long batchExportDelayInterval;

  @Parameter
  @Placement(order = 40, tab = "Tracer Settings")
  @DisplayName("Batch Export Timeout")
  @Optional(defaultValue = "10000")
  @Summary("The Maximum number of milliseconds the exporter will wait for a batch to export before cancelling the export.")
  private long exportTimeout;

  public SpanProcessorConfiguration() {
  }

  public SpanProcessorConfiguration(long maxQueueSize, long maxBatchExportSize, long batchExportDelayInterval,
      long exportTimeout) {
    this.maxQueueSize = maxQueueSize;
    this.maxBatchExportSize = maxBatchExportSize;
    this.batchExportDelayInterval = batchExportDelayInterval;
    this.exportTimeout = exportTimeout;
  }

  public long getMaxQueueSize() {
    return maxQueueSize;
  }

  public long getMaxBatchExportSize() {
    return maxBatchExportSize;
  }

  public long getBatchExportDelayInterval() {
    return batchExportDelayInterval;
  }

  public long getExportTimeout() {
    return exportTimeout;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SpanProcessorConfiguration that = (SpanProcessorConfiguration) o;
    return getMaxQueueSize() == that.getMaxQueueSize() && getMaxBatchExportSize() == that.getMaxBatchExportSize()
        && getBatchExportDelayInterval() == that.getBatchExportDelayInterval()
        && getExportTimeout() == that.getExportTimeout();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMaxQueueSize(), getMaxBatchExportSize(), getBatchExportDelayInterval(),
        getExportTimeout());
  }

  @Override
  public Map<String, String> getConfigMap() {
    Map<String, String> configMap = new HashMap<>();
    configMap.put("otel.bsp.schedule.delay", String.valueOf(this.getBatchExportDelayInterval()));
    configMap.put("otel.bsp.max.queue.size", String.valueOf(this.getMaxQueueSize()));
    configMap.put("otel.bsp.max.export.batch.size", String.valueOf(this.getMaxBatchExportSize()));
    configMap.put("otel.bsp.export.timeout", String.valueOf(this.getExportTimeout()));
    return Collections.unmodifiableMap(configMap);
  }
}
