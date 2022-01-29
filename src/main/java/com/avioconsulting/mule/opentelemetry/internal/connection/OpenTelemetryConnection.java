package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.KeyValuePair;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OpenTelemetryConnection implements TraceContextHandler {

  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryConnection.class);

  public static final String INSTRUMENTATION_VERSION = "0.0.1";
  public static final String INSTRUMENTATION_NAME = "com.avioconsulting.mule.tracing";
  private final TransactionStore transactionStore;
  private static OpenTelemetryConnection openTelemetryConnection;
  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;

  private OpenTelemetryConnection(String instrumentationName, String instrumentationVersion,
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    logger.info("Initialising OpenTelemetry Mule 4 Agent for instrumentation {}:{}", instrumentationName,
        instrumentationVersion);
    // See here for autoconfigure options
    // https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();
    if (openTelemetryConfigWrapper != null) {
      // TODO: Process other config elements for OTEL SDK
      final Map<String, String> configMap = new HashMap<>();
      if (openTelemetryConfigWrapper.getResource() != null) {
        if (openTelemetryConfigWrapper.getResource().getServiceName() != null) {
          configMap.put("otel.service.name", openTelemetryConfigWrapper.getResource().getServiceName());
        }
        configMap.put("otel.resource.attributes",
            KeyValuePair
                .commaSeparatedList(openTelemetryConfigWrapper.getResource().getResourceAttributes()));
      }
      if (openTelemetryConfigWrapper.getExporter() != null) {
        configMap.putAll(openTelemetryConfigWrapper.getExporter().getExporterProperties());
      }
      builder.addPropertiesSupplier(() -> Collections.unmodifiableMap(configMap));
    }
    openTelemetry = builder.build().getOpenTelemetrySdk();
    tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    transactionStore = InMemoryTransactionStore.getInstance();
  }

  public static Optional<OpenTelemetryConnection> get() {
    return Optional.ofNullable(openTelemetryConnection);
  }

  public static synchronized OpenTelemetryConnection getInstance(
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    if (openTelemetryConnection == null) {
      openTelemetryConnection = new OpenTelemetryConnection(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION,
          openTelemetryConfigWrapper);
    }
    return openTelemetryConnection;
  }

  public SpanBuilder spanBuilder(String spanName) {
    return tracer.spanBuilder(spanName);
  }

  public <T> Context getTraceContext(T carrier, TextMapGetter<T> textMapGetter) {
    return openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), carrier, textMapGetter);
  }

  /**
   * Get the trace context information for a given transaction id. This returns
   * a @{@link Map<String, String>} with
   * at least one entry with key {@link TransactionStore#TRACE_TRANSACTION_ID} and
   * transactionId as value.
   * The other entries in the map depends on the propagator used.
   *
   * For W3C Trace Context Propagator, it can contain entries for `traceparent`
   * and optionally `tracestate`.
   *
   * @param transactionId
   *            Local transaction id
   * @return @{@link Map<String, String}
   */
  public Map<String, String> getTraceContext(String transactionId) {
    Context transactionContext = getTransactionStore().getTransactionContext(transactionId);
    Map<String, String> traceContext = new HashMap<>();
    traceContext.put(TransactionStore.TRACE_TRANSACTION_ID, transactionId);
    try (Scope scope = transactionContext.makeCurrent()) {
      injectTraceContext(traceContext, HashMapTextMapSetter.INSTANCE);
    }
    return Collections.unmodifiableMap(traceContext);
  }

  public <T> void injectTraceContext(T carrier, TextMapSetter<T> textMapSetter) {
    openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, textMapSetter);
  }

  public TransactionStore getTransactionStore() {
    return transactionStore;
  }

  public void invalidate() {

  }

  public static enum HashMapTextMapSetter implements TextMapSetter<Map<String, String>> {
    INSTANCE;

    @Override
    public void set(@Nullable Map<String, String> carrier, String key, String value) {
      if (carrier != null)
        carrier.put(key, value);
    }
  }
}
