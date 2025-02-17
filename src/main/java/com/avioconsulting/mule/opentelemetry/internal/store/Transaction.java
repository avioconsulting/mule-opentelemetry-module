package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

public class Transaction implements TransactionMeta {
  private final String transactionId;
  private final String rootFlowName;
  private final FlowSpan rootFlowSpan;
  private final String traceId;
  private final Instant startTime;
  private Instant endTime;

  public Transaction(String transactionId, String traceId, String rootFlowName, FlowSpan rootFlowSpan,
      Instant startTime) {
    this.transactionId = transactionId;
    this.rootFlowName = rootFlowName;
    this.rootFlowSpan = rootFlowSpan;
    this.traceId = traceId;
    this.startTime = startTime;
  }

  @Override
  public String getTransactionId() {
    return transactionId;
  }

  @Override
  public String getRootFlowName() {
    return rootFlowName;
  }

  public FlowSpan getRootFlowSpan() {
    return rootFlowSpan;
  }

  @Override
  public Span getSpan() {
    return rootFlowSpan.getSpan();
  }

  @Override
  public String getTraceId() {
    return traceId;
  }

  @Override
  public Instant getStartTime() {
    return startTime;
  }

  @Override
  public Instant getEndTime() {
    return endTime;
  }

  private void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  public Map<String, String> getTags() {
    return getRootFlowSpan().getTags();
  }

  /**
   * Transaction ends when end time has been set and all associated child flows
   * have also ended.
   * 
   * @return true if transaction has ended, otherwise false
   */
  public boolean hasEnded() {
    return endTime != null && getRootFlowSpan().childFlowsEnded();
  }

  /**
   * Ends the root flow associated with this transaction and sets the
   * {@link this#endTime} of the transaction.
   * Ending a transaction does not necessarily mean that all child flows
   * associated with the processing of the root flow have ended.
   * Check {@link #hasEnded()} to validate if the full transaction has ended.
   * Ending any child flows are handled by invoking
   * {@link FlowSpan#endChildFlow(TraceComponent, Consumer)} for each of the child
   * flow.
   * 
   * @param traceComponent
   *            {@link TraceComponent} for the root flow span
   * @param endSpan
   *            {@link Consumer<Span>} to let caller's make changes to the
   *            associated {@link Span} for root flow
   */
  public void endRootFlow(TraceComponent traceComponent, Consumer<Span> endSpan) {
    endSpan.accept(getRootFlowSpan().getSpan());
    this.setEndTime(traceComponent.getEndTime());
  }
}
