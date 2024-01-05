package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.avioconsulting.mule.opentelemetry.internal.processor.util.HttpSpanUtil.*;

public class FlowSpan implements Serializable {
  private final String flowName;
  private String rootSpanName;
  private final Span span;
  private final String transactionId;
  private final Map<String, ProcessorSpan> childSpans = new ConcurrentHashMap<>();
  private Map<String, String> tags = new HashMap<>();
  private String apikitConfigName;

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

  public String getApikitConfigName() {
    return apikitConfigName;
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
    extractAPIKitConfigName(traceComponent);
    resetSpanNameIfNeeded(traceComponent);
    Span span = spanBuilder.startSpan();
    ProcessorSpan ps = new ProcessorSpan(span, traceComponent.getLocation(), transactionId,
        traceComponent.getStartTime(), flowName).setTags(traceComponent.getTags());
    childSpans.put(traceComponent.getLocation(), ps);
    return ps;
  }

  private void resetSpanNameIfNeeded(TraceComponent traceComponent) {
    if (!PropertiesUtil.isUseAPIKitSpanNames())
      return;
    if (apikitConfigName != null && traceComponent.getName().endsWith(":" + apikitConfigName)) {
      if (rootSpanName.endsWith("/*")) { // Wildcard listener for HTTP APIKit Router
        String spanName = apiKitRoutePath(traceComponent.getTags(), getRootSpanName());
        getSpan().updateName(spanName);
      }
    }
  }

  private void extractAPIKitConfigName(TraceComponent traceComponent) {
    if (apikitConfigName == null
        && "apikit"
            .equals(traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey()))
        && "router".equals(traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey()))) {
      apikitConfigName = traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey());
    }
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

  public String getRootSpanName() {
    return rootSpanName;
  }

  public FlowSpan setRootSpanName(String rootSpanName) {
    this.rootSpanName = rootSpanName;
    return this;
  }
}
