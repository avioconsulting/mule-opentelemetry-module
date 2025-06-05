package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;

import java.time.Instant;
import java.util.function.Consumer;

public abstract class AbstractTransaction implements Transaction {
  private final String transactionId;
  private final String rootSpanName;
  private final String traceId;
  private final Instant startTime;
  private Instant endTime;

  public AbstractTransaction(String transactionId, String traceId, String rootSpanName,
      Instant startTime) {
    this.transactionId = transactionId;
    this.rootSpanName = rootSpanName;
    this.traceId = traceId;
    this.startTime = startTime;
  }

  @Override
  public String getTransactionId() {
    return transactionId;
  }

  /**
   * Use {@link #getRootSpanName()} instead.
   * 
   * @return root flow name associated with this transaction.
   */
  @Override
  @Deprecated
  public String getRootFlowName() {
    return rootSpanName;
  }

  @Override
  public String getRootSpanName() {
    return rootSpanName;
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

  /**
   * Ends the root flow associated with this transaction and sets the
   * {@link #endTime} of the transaction.
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
   *            {@link Consumer} of a {@link Span} to let caller's make changes to
   *            the
   *            associated {@link Span} for root flow
   */
  public void endRootSpan(TraceComponent traceComponent, Consumer<Span> endSpan) {
    endSpan.accept(getTransactionSpan());
    this.setEndTime(traceComponent.getEndTime());
  }

}
