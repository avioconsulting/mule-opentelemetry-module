package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

public class FlowTransaction extends AbstractTransaction {

  private final FlowSpan rootFlowSpan;

  public FlowTransaction(String transactionId, String traceId, String rootFlowName, FlowSpan rootFlowSpan,
      Instant startTime) {
    super(transactionId, traceId, rootFlowName, startTime);
    this.rootFlowSpan = rootFlowSpan;
  }

  private FlowSpan getRootFlowSpan() {
    return rootFlowSpan;
  }

  @Override
  public Span getSpan() {
    return getRootFlowSpan().getSpan();
  }

  @Override
  public Map<String, String> getTags() {
    return getRootFlowSpan().getTags();
  }

  @Override
  public Span getTransactionSpan() {
    return getRootFlowSpan().getSpan();
  }

  @Override
  public void addChildTransaction(TraceComponent traceComponent, SpanBuilder spanBuilder) {
    getRootFlowSpan().addChildContainer(traceComponent, spanBuilder);
  }

  @Override
  public TransactionMeta endChildTransaction(TraceComponent traceComponent, Consumer<Span> endSpan) {
    return getRootFlowSpan().endChildContainer(traceComponent, endSpan);
  }

  @Override
  public ProcessorSpan findSpan(String location) {
    return getRootFlowSpan().findSpan(location);
  }

  /**
   * Transaction ends when end time has been set and all associated child flows
   * have also ended.
   *
   * @return true if transaction has ended, otherwise false
   */
  public boolean hasEnded() {
    return getEndTime() != null && getRootFlowSpan().childFlowsEnded();
  }

  @Override
  public SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    return getRootFlowSpan().addProcessorSpan(containerName, traceComponent, spanBuilder);
  }

  @Override
  public SpanMeta endProcessorSpan(TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    return getRootFlowSpan().endProcessorSpan(traceComponent, spanUpdater, endTime);
  }
}
