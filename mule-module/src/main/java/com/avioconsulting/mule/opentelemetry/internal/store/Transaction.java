package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * A Mule Trace transaction representation
 */
public interface Transaction extends TransactionMeta {

  boolean hasEnded();

  void endRootSpan(TraceComponent traceComponent, Consumer<Span> endSpan);

  SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder);

  SpanMeta endProcessorSpan(TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime);

  Span getTransactionSpan();

  void addChildTransaction(TraceComponent traceComponent, SpanBuilder spanBuilder);

  TransactionMeta endChildTransaction(TraceComponent traceComponent, Consumer<Span> endSpan);

  ProcessorSpan findSpan(String location);
}
