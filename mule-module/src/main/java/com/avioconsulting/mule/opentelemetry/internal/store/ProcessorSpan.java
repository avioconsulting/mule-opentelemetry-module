package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessorSpan implements SpanMeta {

  private final Span span;
  private final String location;
  private final String transactionId;
  private final Instant startTime;
  private Instant endTime;
  private final String flowName;
  private Context context;
  private final Map<String, String> tags = new HashMap<>();
  private ProcessorSpan parentSpan;
  private final AtomicLong siblingCount = new AtomicLong();

  public ProcessorSpan(Span span, String location, String transactionId, Instant startTime, String flowName) {
    this(span, location, transactionId, startTime, flowName, -1);
  }

  public ProcessorSpan(Span span, String location, String transactionId, Instant startTime, String flowName,
      long siblings) {
    this.span = span;
    this.location = location;
    this.transactionId = transactionId;
    this.startTime = startTime;
    this.flowName = flowName;
    siblingCount.set(siblings);
  }

  @Override
  public String getTransactionId() {
    return transactionId;
  }

  @Override
  public String getRootFlowName() {
    return flowName;
  }

  @Override
  public String getRootSpanName() {
    return flowName;
  }

  public long getSiblings() {
    return siblingCount.longValue();
  }

  public void decrementActiveSiblingCount() {
    siblingCount.decrementAndGet();
  }

  @Override
  public String getTraceId() {
    return span.getSpanContext().getTraceId();
  }

  @Override
  public String getSpanId() {
    return span.getSpanContext().getSpanId();
  }

  @Override
  public Context getContext() {
    if (context == null)
      context = span.storeInContext(Context.current());
    return context;
  }

  public Span getSpan() {
    return span;
  }

  public String getLocation() {
    return location;
  }

  @Override
  public Instant getStartTime() {
    return startTime;
  }

  @Override
  public Instant getEndTime() {
    return endTime;
  }

  public ProcessorSpan setEndTime(Instant endTime) {
    this.endTime = endTime;
    return this;
  }

  @Override
  public Map<String, String> getTags() {
    return tags;
  }

  public ProcessorSpan setTags(Map<String, String> tags) {
    this.tags.putAll(tags);
    return this;
  }

  public ProcessorSpan getParentSpan() {
    return parentSpan;
  }

  public ProcessorSpan setParentSpan(ProcessorSpan parentSpan) {
    this.parentSpan = parentSpan;
    return this;
  }

  @Override
  public String toString() {
    return "ProcessorSpan{" +
        "span=" + span +
        ", location='" + location + '\'' +
        ", transactionId='" + transactionId + '\'' +
        ", startTime=" + startTime +
        ", endTime=" + endTime +
        ", flowName='" + flowName + '\'' +
        ", context=" + context +
        ", tags=" + tags +
        '}';
  }
}
