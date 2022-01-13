package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenTelemetryConnection implements TraceContextHandler {

  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryConnection.class);

  public static final String INSTRUMENTATION_VERSION = "0.0.1";
  public static final String INSTRUMENTATION_NAME = "com.avioconsulting.mule.tracing";
  private final TransactionStore transactionStore;
  private static OpenTelemetryConnection starter;
  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;

  private OpenTelemetryConnection(String instrumentationName, String instrumentationVersion) {
    logger.info("Initialising OpenTelemetry Mule 4 Agent for instrumentation {}:{}", instrumentationName,
        instrumentationVersion);
    // See here for autoconfigure options
    // https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
    openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
    tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    transactionStore = InMemoryTransactionStore.getInstance();
  }

  public static synchronized OpenTelemetryConnection getInstance() {
    if (starter == null) {
      starter = new OpenTelemetryConnection(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }
    return starter;
  }

  public SpanBuilder spanBuilder(String spanName) {
    return tracer.spanBuilder(spanName);
  }

  public <T> Context getTraceContext(T carrier, TextMapGetter<T> textMapGetter) {
    return openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), carrier, textMapGetter);
  }

  public <T> void injectTraceContext(T carrier, TextMapSetter<T> textMapSetter) {
    openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, textMapSetter);
  }

  public TransactionStore getTransactionStore() {
    return transactionStore;
  }

  public void invalidate() {

  }
}
