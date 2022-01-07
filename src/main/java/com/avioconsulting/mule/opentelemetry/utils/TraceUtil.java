package com.avioconsulting.mule.opentelemetry.utils;

import com.avioconsulting.mule.opentelemetry.OpenTelemetryStarter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

public class TraceUtil {
    static public <T> Context getTraceContext(T object, TextMapGetter<T> textMapGetter) {
        return OpenTelemetryStarter.getInstance().getOpenTelemetry().getPropagators().getTextMapPropagator().extract(Context.current(), object, textMapGetter);
    }
}
