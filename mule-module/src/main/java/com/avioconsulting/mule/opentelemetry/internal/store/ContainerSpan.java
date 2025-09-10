package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.ComponentEventContext;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.StringUtil.UNDERSCORE;

public class ContainerSpan implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerSpan.class);

  private final String containerName;
  private String rootSpanName;
  private final Span span;
  private final String transactionId;
  private final Map<String, ProcessorSpan> childSpans = new ConcurrentHashMap<>();
  private Map<String, String> tags = new HashMap<>();
  private final AtomicInteger childContainerCounter = new AtomicInteger();
  private final Context rootContext;

  private final ProcessorSpan rootProcessorSpan;

  public ContainerSpan(String containerName, Span span, TraceComponent traceComponent) {
    this.containerName = containerName;
    this.span = span;
    this.transactionId = traceComponent.getTransactionId();
    this.rootContext = span.storeInContext(Context.current());
    traceComponent.copyTagsTo(tags);
    setRootSpanName(traceComponent.getSpanName());
    tagsToAttributes(traceComponent, span);
    rootProcessorSpan = new ProcessorSpan(span, traceComponent.getLocation(),
        transactionId,
        traceComponent.getStartTime(), traceComponent.getSpanName());
  }

  public Span getSpan() {
    return span;
  }

  public String getContainerName() {
    return containerName;
  }

  private Context getRootContext() {
    return rootContext;
  }

  public ProcessorSpan getRootProcessorSpan() {
    return rootProcessorSpan;
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
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Adding Span at location {} for container {} trace transaction {} context {}",
          traceComponent.contextScopedLocation(),
          this.getRootSpanName(),
          this.transactionId, this.getSpan().getSpanContext().toString());
    }
    ProcessorSpan parentSpan = null;
    if (containerName != null) {
      if (getContainerName().equals(containerName)) {
        spanBuilder.setParent(getRootContext());
      } else {
        parentSpan = getParentSpan(traceComponent, containerName);
        if (parentSpan == null) {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Parent span not found for {}/{}. Child span keys - {}",
                traceComponent.getEventContextId(), traceComponent.getLocation(),
                childSpans.keySet());
          }
          parentSpan = rootProcessorSpan;
        }
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Parent span existence check for {} at {}", traceComponent.getLocation(),
              parentSpan.getLocation());
        }
        spanBuilder.setParent(parentSpan.getContext());
      }
    }
    Span span = spanBuilder.startSpan();
    ProcessorSpan ps = new ProcessorSpan(span, traceComponent.getLocation(), transactionId,
        traceComponent.getStartTime(), this.containerName, traceComponent.getSiblings());
    traceComponent.copyTagsTo(ps.getTags());
    ps.setParentSpan(parentSpan);
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Adding span for {}:{} - {}", traceComponent.contextScopedLocation(),
          traceComponent.getSpanName(),
          span.getSpanContext().getSpanId());
    }
    childSpans.putIfAbsent(traceComponent.contextScopedLocation(), ps);
    return ps;
  }

  public SpanMeta addChildContainer(TraceComponent traceComponent, SpanBuilder spanBuilder) {
    SpanMeta spanMeta = addProcessorSpan(null, traceComponent, spanBuilder);
    childContainerCounter.incrementAndGet();
    return spanMeta;
  }

  public ProcessorSpan endChildContainer(TraceComponent traceComponent, Consumer<Span> endSpan) {
    ProcessorSpan processorSpan = findSpan(traceComponent.contextScopedPath(traceComponent.getName()));
    if (processorSpan == null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Attempting to find in parent scopes for {} in list {}", traceComponent,
            childSpans);
      }
      processorSpan = getParentSpan(traceComponent, traceComponent.getName());
    }
    if (processorSpan != null) {
      endSpan.accept(processorSpan.getSpan());
      processorSpan.setEndTime(traceComponent.getEndTime());
      childContainerCounter.decrementAndGet();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Ended a span of a container {} invoked with flow-ref for transaction {} ",
            traceComponent.getName(), traceComponent.getTransactionId());
      }
    } else {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("No Processor span found for {} ", traceComponent);
      }
    }
    return processorSpan;
  }

  private ProcessorSpan getParentSpan(ComponentEventContext context, String container) {
    for (int i = 0; i < context.contextNestingLevel(); i++) {
      ProcessorSpan processorSpan = childSpans.get(context.contextScopedPath(container, i));
      if (processorSpan != null)
        return processorSpan;
    }
    return null;
  }

  public SpanMeta endProcessorSpan(TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Ending Span at location {} for container {} trace transaction {} context {}",
          traceComponent.contextScopedLocation(),
          this.getRootSpanName(),
          this.transactionId, this.getSpan().getSpanContext().toString());
    }
    if (childSpans.containsKey(traceComponent.contextScopedLocation())) {
      ProcessorSpan removed = Objects.requireNonNull(childSpans.remove(traceComponent.contextScopedLocation()),
          "Missing child span at location " + traceComponent.contextScopedLocation() + " for flow "
              + getRootSpanName()
              + " trace transaction " + transactionId + " context "
              + getSpan().getSpanContext().toString());
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Removing span for {} - {}", traceComponent.contextScopedLocation(), removed.getSpanId());
      }
      endRouteSpans(traceComponent, endTime);
      removed.setEndTime(endTime);
      if (spanUpdater != null)
        spanUpdater.accept(removed.getSpan());
      removed.getSpan().end(endTime);
      if (removed.getParentSpan() != null && removed.getParentSpan().getSiblings() > 0) {
        removed.getParentSpan().decrementActiveSiblingCount();
      }
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
  void endRouteSpans(TraceComponent traceComponent, Instant endTime) {
    if (traceComponent.getComponentLocation() == null ||
        !TypedComponentIdentifier.ComponentType.ROUTER
            .equals(traceComponent.getComponentLocation().getComponentIdentifier().getType()))
      return;

    String parentContextId = traceComponent.getEventContextId();
    String routeSuffix = "/" + traceComponent.getLocation() + "/route/";

    List<String> routeSpanKeys = new ArrayList<>();

    for (String key : childSpans.keySet()) {
      if (isRouteKey(key, parentContextId, routeSuffix)) {
        routeSpanKeys.add(key);
      }
    }
    for (String key : routeSpanKeys) {
      ProcessorSpan span = childSpans.remove(key);
      if (span != null) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Ending Route Span at location {} for container {} trace transaction {} context {}",
              key, this.getRootSpanName(), this.transactionId,
              span.getSpan().getSpanContext());
        }
        span.getSpan().end(endTime);
      }
    }
  }

  private boolean isRouteKey(String spanKey, String parentContextId, String routeSuffix) {
    // Fast checks:
    // 1. Must start with parent context ID
    if (!spanKey.startsWith(parentContextId)) {
      return false;
    }

    // 2. Must contain the route suffix
    int routeIndex = spanKey.indexOf(routeSuffix, parentContextId.length());
    if (routeIndex == -1) {
      return false;
    }

    // 3. Validate the middle part (empty or _digits)
    String middlePart = spanKey.substring(parentContextId.length(), routeIndex);
    if (!middlePart.isEmpty()) {
      // Must be _<numbers> pattern
      if (!middlePart.startsWith(UNDERSCORE) || middlePart.length() < 2) {
        return false;
      }
      // Check if rest are digits
      for (int i = 1; i < middlePart.length(); i++) {
        if (!Character.isDigit(middlePart.charAt(i))) {
          return false;
        }
      }
    }

    // 4. Validate route number at the end
    String routeNumber = spanKey.substring(routeIndex + routeSuffix.length());
    for (int i = 0; i < routeNumber.length(); i++) {
      if (!Character.isDigit(routeNumber.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public ProcessorSpan findSpan(String location) {
    ProcessorSpan processorSpan = childSpans.get(location);
    if (processorSpan == null)
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Could not find span for location {}  in the list {}", location, childSpans);
      }
    return processorSpan;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public ContainerSpan setTags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }

  public String getRootSpanName() {
    return rootSpanName;
  }

  public ContainerSpan setRootSpanName(String rootSpanName) {
    this.rootSpanName = rootSpanName;
    return this;
  }

  public boolean childContainersEnded() {
    return childContainerCounter.get() <= 0;
  }

}
