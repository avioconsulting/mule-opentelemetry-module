package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import io.opentelemetry.api.trace.Span;

import java.time.Instant;
import java.util.Map;

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

  public void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  public Map<String, String> getTags() {
    return getRootFlowSpan().getTags();
  }
}
