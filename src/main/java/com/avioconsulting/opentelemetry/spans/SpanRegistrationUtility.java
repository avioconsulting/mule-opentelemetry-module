package com.avioconsulting.opentelemetry.spans;

import com.avioconsulting.opentelemetry.OpenTelemetryStarter;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

public class SpanRegistrationUtility {

    public static final String INSTRUMENTATION_VERSION = "0.0.1";
    public static final String INSTRUMENTATION_NAME = "com.avioconsulting.tracing";

    private static final Tracer tracer = OpenTelemetryStarter.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);

    public static SpanBuilder createSpan(String spanName) {
        return tracer.spanBuilder(spanName);
    }
}
