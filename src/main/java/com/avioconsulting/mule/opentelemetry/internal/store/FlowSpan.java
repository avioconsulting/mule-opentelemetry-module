package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FlowSpan implements Serializable {
  private final String flowName;
  private final Span span;
  private final String transactionId;
  private final Map<String, ProcessorSpan> childSpans = new ConcurrentHashMap<>();
  private Map<String, String> tags = new HashMap<>();

  public FlowSpan(String flowName, Span span, String transactionId) {
    this.flowName = flowName;
    this.span = span;
    this.transactionId = transactionId;
  }

  public Span getSpan() {
    return span;
  }

  public String getFlowName() {
    return flowName;
  }

  /**
   * Add a span created from given {@code SpanBuilder} for the processor
   * identified at the given location {@code String}.
   * When containerName {@code String} is provided, an existing span of that
   * container (eg. Flow) is set as the parent span of this processor span.
   *
   * @param containerName
   *            {@link String}
   * @param traceComponent
   *            {@link TraceComponent}
   * @param spanBuilder
   *            {@link SpanBuilder}
   * @return Span
   */
  public SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    if (containerName != null) {
      ProcessorSpan ps = new ProcessorSpan(getSpan(), traceComponent.getLocation(), transactionId,
          traceComponent.getStartTime(), flowName).setTags(getTags());
      ProcessorSpan parentSpan = childSpans.getOrDefault(containerName, ps);
      spanBuilder.setParent(parentSpan.getContext());
    }
    Span span = spanBuilder.startSpan();
    ProcessorSpan ps = new ProcessorSpan(span, traceComponent.getLocation(), transactionId,
        traceComponent.getStartTime(), flowName).setTags(traceComponent.getTags());
    childSpans.put(traceComponent.getLocation(), ps);
    return ps;
  }

  public SpanMeta endProcessorSpan(String location, Consumer<ProcessorSpan> spanUpdater, Instant endTime) {
    if (childSpans.containsKey(location)) {
      ProcessorSpan removed = childSpans.remove(location);
      removed.setEndTime(endTime);
      if (spanUpdater != null)
        spanUpdater.accept(removed);
      removed.getSpan().end(endTime);
      return removed;
    }
    return null;
  }

  public ProcessorSpan findSpan(String location) {
    return childSpans.get(location);
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public FlowSpan setTags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }
}
