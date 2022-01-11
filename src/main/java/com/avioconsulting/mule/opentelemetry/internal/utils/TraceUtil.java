package com.avioconsulting.mule.opentelemetry.internal.utils;

import com.avioconsulting.mule.opentelemetry.internal.OpenTelemetryStarter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

public class TraceUtil {

    /**
     * Extract OpenTelemetry @{@link Context} from provided request carrier. Current Default Implementation supports @{@link io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator}
     * and @{@link io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator} without any additional configuration.
     *
     * @param carrier Request attributes carrier to extract context headers. For HTTP Listener based flows, this will be an instance of HTTPRequestAttributes.
     * @param textMapGetter A getter implementation to extract context attributes from carrier.
     * @return @{@link Context}
     */
    static public <T> Context getTraceContext(T carrier, TextMapGetter<T> textMapGetter) {
        return OpenTelemetryStarter.getInstance().getOpenTelemetry().getPropagators().getTextMapPropagator().extract(Context.current(), carrier, textMapGetter);
    }

    static public <T> void injectTraceContext(T carrier, TextMapSetter<T> textMapSetter) {
        OpenTelemetryStarter.getInstance().getOpenTelemetry().getPropagators().getTextMapPropagator().inject(Context.current(), carrier, textMapSetter);
    }
}
