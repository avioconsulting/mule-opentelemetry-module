package com.avioconsulting.mule.opentelemetry.internal.auth;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;

public final class AuthenticatedMetricExporter implements MetricExporter {
  private final MetricExporter delegate;
  private final AuthenticationExportGate gate;
  private CompletableResultCode shutdown;

  public AuthenticatedMetricExporter(MetricExporter delegate, AuthenticationExportGate gate) {
    this.delegate = delegate;
    this.gate = gate;
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    return gate.export(() -> delegate.export(metrics));
  }

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType type) {
    return delegate.getAggregationTemporality(type);
  }

  @Override
  public Aggregation getDefaultAggregation(InstrumentType type) {
    return delegate.getDefaultAggregation(type);
  }

  @Override
  public MemoryMode getMemoryMode() {
    return delegate.getMemoryMode();
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public synchronized CompletableResultCode shutdown() {
    if (shutdown == null) {
      shutdown = delegate.shutdown();
    }
    return shutdown;
  }

  @Override
  public String toString() {
    return "AuthenticatedMetricExporter";
  }
}
