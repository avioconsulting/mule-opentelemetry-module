package com.avioconsulting.mule.opentelemetry.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public Span addProcessorSpan(String location, SpanBuilder spanBuilder){
        if(ending || ended) throw new UnsupportedOperationException("Flow " + flowName + " span " + (ended ? "has ended." : "is ending."));
        Span span = spanBuilder.setParent(Context.current().with(getSpan())).startSpan();
        childSpans.put(location, span);
        return span;
    }
    public void endProcessorSpan(String location){
        if((!ending || ended) && childSpans.containsKey(location)) {
            Span removed = childSpans.remove(location);
            removed.end();
        }
    }
    public void end() {
        ending = true;
        childSpans.forEach( (location,span) -> span.end());
        span.end();
        ended = true;
    }
}
