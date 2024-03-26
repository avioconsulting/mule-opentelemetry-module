package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.ComponentEventContext;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.avioconsulting.mule.opentelemetry.internal.processor.util.HttpSpanUtil.apiKitRoutePath;

public class FlowSpan implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowSpan.class);

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
    LOGGER.trace("Adding Span at location {} for flow {} trace transaction {} context {}",
        traceComponent.contextScopedLocation(),
        this.getRootSpanName(),
        this.transactionId, this.getSpan().getSpanContext().toString());
    if (containerName != null) {
      if (getFlowName().equals(containerName)) {
        spanBuilder.setParent(getSpan().storeInContext(Context.current()));
      } else {
        String contextScopedContainer = traceComponent.contextScopedPath(containerName);
        ProcessorSpan ps = new ProcessorSpan(getSpan(), traceComponent.getLocation(), transactionId,
            traceComponent.getStartTime(), flowName).setTags(getTags());
        ProcessorSpan parentSpan = getParentSpan(traceComponent, containerName);
        if (parentSpan == null) {
          LOGGER.debug("Parent span not found for {}. Child span keys - {}", contextScopedContainer,
              childSpans.keySet());
          parentSpan = ps;
        }
        LOGGER.debug("Parent span existence check for {} at {}", traceComponent.getLocation(),
            parentSpan.getLocation());
        spanBuilder.setParent(parentSpan.getContext());
      }
    }
    extractAPIKitConfigName(traceComponent);
    resetSpanNameIfNeeded(traceComponent);
    Span span = spanBuilder.startSpan();
    ProcessorSpan ps = new ProcessorSpan(span, traceComponent.getLocation(), transactionId,
        traceComponent.getStartTime(), flowName).setTags(traceComponent.getTags());
    childSpans.put(traceComponent.contextScopedLocation(), ps);
    return ps;
  }

  private ProcessorSpan getParentSpan(ComponentEventContext context, String container) {
    for (int i = 0; i < context.contextNestingLevel(); i++) {
      ProcessorSpan processorSpan = childSpans.get(context.contextCopedPath(container, i));
      if (processorSpan != null)
        return processorSpan;
    }
    return null;
  }

  private void resetSpanNameIfNeeded(TraceComponent traceComponent) {
    if (!PropertiesUtil.isUseAPIKitSpanNames())
      return;
    if (apikitConfigName != null && ComponentsUtil.isFlowTrace(traceComponent)
        && traceComponent.getName().endsWith(":" + apikitConfigName)) {
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

  public SpanMeta endProcessorSpan(TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    LOGGER.trace("Ending Span at location {} for flow {} trace transaction {} context {}",
        traceComponent.contextScopedLocation(),
        this.getRootSpanName(),
        this.transactionId, this.getSpan().getSpanContext().toString());
    if (childSpans.containsKey(traceComponent.contextScopedLocation())) {
      ProcessorSpan removed = Objects.requireNonNull(childSpans.remove(traceComponent.contextScopedLocation()),
          "Missing child span at location " + traceComponent.contextScopedLocation() + " for flow "
              + getRootSpanName()
              + " trace transaction " + transactionId + " context "
              + getSpan().getSpanContext().toString());

      endRouteSpans(traceComponent, endTime);

      removed.setEndTime(endTime);
      if (spanUpdater != null)
        spanUpdater.accept(removed.getSpan());
      removed.getSpan().end(endTime);
      return removed;
    }
    return null;
  }

  /**
   * <pre>
   * Router's Routes do not have any notification or events attach to them. For ending a route span, it is tied to the
   * completion of the Router itself.
   *
   * For example, when scatter-gather ends, all the routes inside it are also marked as completed.
   *
   * If `flow-controls:scatter-gather:sub-flow/processors/1` location represents a scatter-gather component with 3 routes inside it,
   * following will be the route spans created for it -
   *
   * <ul>
   *  <li>flow-controls:scatter-gather:sub-flow/processors/1/route/0</li>
   *  <li>flow-controls:scatter-gather:sub-flow/processors/1/route/1</li>
   * </ul>
   *
   * When combined with {@link ComponentEventContext#getEventContextId()} scatter-gather at
   * `3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373/flow-controls:scatter-gather:sub-flow/processors/1` ends,
   * it will also end the routes  -
   *
   * <ul>
   *  <li>3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373<b>_646839410</b>/flow-controls:scatter-gather:sub-flow/processors/1/route/0</li>
   *  <li>3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373<b>_75520183</b>/flow-controls:scatter-gather:sub-flow/processors/1/route/1</li>
   * </ul>
   *
   * Due to this behavior, all route spans will have same processing time as the parent router span.
   *
   * </pre>
   * 
   * @param traceComponent
   *            {@link TraceComponent}
   * @param endTime
   *            {@link Instant}
   */
  private void endRouteSpans(TraceComponent traceComponent, Instant endTime) {
    if (!TypedComponentIdentifier.ComponentType.ROUTER
        .equals(traceComponent.getComponentLocation().getComponentIdentifier().getType()))
      return;
    // Location string may contain characters not allowed in REGEX, so let's quote
    // it with \Q\E
    String regexPattern = String.format("^%s(_\\d.*)?\\/%s\\/route\\/\\d*$", traceComponent.getEventContextId(),
        Pattern.quote(traceComponent.getLocation()));
    Pattern pattern = Pattern.compile(regexPattern);
    Predicate<String> predicate = pattern.asPredicate();
    childSpans.keySet().stream().filter(predicate).forEach(k -> {
      ProcessorSpan removed = childSpans.remove(k);
      if (removed != null) {
        LOGGER.trace("Ending Route Span at location {} for flow {} trace transaction {} context {}",
            k,
            this.getRootSpanName(),
            this.transactionId, removed.getSpan().getSpanContext());
        removed.getSpan().end(endTime);
      }
    });
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
