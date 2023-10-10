package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FlowSpan implements Serializable {
  private final String flowName;
  private final Span span;
  private boolean ending = false;
  private final Map<String, Span> childSpans = new ConcurrentHashMap<>();
  private boolean ended = false;

  public FlowSpan(String flowName, Span span) {
    this.flowName = flowName;
    this.span = span;
  }

  public Span getSpan() {
    return span;
  }

  /**
   * Add a span created from given {@code SpanBuilder} for the processor
   * identified at the given location {@code String}.
   * When containerName {@code String} is provided, an existing span of that
   * container (eg. Flow) is set as the parent span of this processor span.
   * 
   * @param containerName
   *            {@link String}
   * @param location
   *            {@link String}
   * @param spanBuilder
   *            {@link SpanBuilder}
   * @return Span
   */
  public Span addProcessorSpan(String containerName, String location, SpanBuilder spanBuilder) {
    if (ending || ended)
      throw new UnsupportedOperationException(
          "Flow " + flowName + " span " + (ended ? "has ended." : "is ending."));
    if (containerName != null) {
      Span parentSpan = childSpans.getOrDefault(containerName, getSpan());
      spanBuilder.setParent(Context.current().with(parentSpan));
    }
    Span span = spanBuilder.startSpan();
    childSpans.put(location, span);
    return span;
  }

  public void endProcessorSpan(String location, Consumer<Span> spanUpdater, Instant endTime) {
    if ((!ending || ended) && childSpans.containsKey(location)) {
      Span removed = childSpans.remove(location);
      if (spanUpdater != null)
        spanUpdater.accept(removed);
      removed.end(endTime);
    }
  }

  public void end(Instant endTime) {
    ending = true;
    childSpans.forEach((location, span) -> span.end(endTime));
    span.end(endTime);
    ended = true;
  }

  public Optional<Span> findSpan(String location) {
    return Optional.ofNullable(childSpans.get(location));
  }
}
