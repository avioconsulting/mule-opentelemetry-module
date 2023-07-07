package com.avioconsulting.mule.opentelemetry.internal.store;

import java.io.Serializable;

public class Transaction implements Serializable {
  private final String transactionId;
  private final String rootFlowName;
  private final FlowSpan rootFlowSpan;
  private final String traceId;
  private final String spanId;

  public Transaction(String transactionId, String traceId, String rootFlowName, FlowSpan rootFlowSpan) {
    this.transactionId = transactionId;
    this.rootFlowName = rootFlowName;
    this.rootFlowSpan = rootFlowSpan;
    this.traceId = traceId;
    this.spanId = rootFlowSpan.getSpan().getSpanContext().getSpanId();
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getRootFlowName() {
    return rootFlowName;
  }

  public FlowSpan getRootFlowSpan() {
    return rootFlowSpan;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getSpanId() {
    return spanId;
  }
}
