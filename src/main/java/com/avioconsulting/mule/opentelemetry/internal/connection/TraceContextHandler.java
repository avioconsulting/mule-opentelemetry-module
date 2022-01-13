package com.avioconsulting.mule.opentelemetry.internal.connection;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

public interface TraceContextHandler {

  <T> Context getTraceContext(T carrier, TextMapGetter<T> textMapGetter);

  <T> void injectTraceContext(T carrier, TextMapSetter<T> textMapSetter);
}
