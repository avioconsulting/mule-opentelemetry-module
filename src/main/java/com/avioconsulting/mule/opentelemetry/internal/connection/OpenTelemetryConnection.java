package com.avioconsulting.mule.opentelemetry.internal.connection;

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
import org.mule.runtime.api.component.location.ComponentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class OpenTelemetryConnection implements TraceContextHandler {

  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryConnection.class);

  /**
   * Instrumentation version must be picked from the module's artifact version.
   * This is a fallback for any dev testing.
   */
  private static final String INSTRUMENTATION_VERSION = "0.0.1-DEV";
  /**
   * Instrumentation Name must be picked from the module's artifact id.
   * This is a fallback for any dev testing.
   */
  private static final String INSTRUMENTATION_NAME = "mule-opentelemetry-module-DEV";
  private final TransactionStore transactionStore;
  private static OpenTelemetryConnection openTelemetryConnection;
  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;

  private OpenTelemetryConnection(OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {

    Properties properties = getModuleProperties();
    String instrumentationVersion = properties.getProperty("module.version", INSTRUMENTATION_VERSION);
    String instrumentationName = properties.getProperty("module.artifactId", INSTRUMENTATION_NAME);

    logger.info("Initialising OpenTelemetry Mule 4 Agent for instrumentation {}:{}", instrumentationName,
        instrumentationVersion);
    // See here for autoconfigure options
    // https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();
    if (openTelemetryConfigWrapper != null) {
      // TODO: Process other config elements for OTEL SDK
      final Map<String, String> configMap = new HashMap<>();
      if (openTelemetryConfigWrapper.getResource() != null) {
        configMap.putAll(openTelemetryConfigWrapper.getResource().getConfigMap());
      }
      if (openTelemetryConfigWrapper.getExporter() != null) {
        configMap.putAll(openTelemetryConfigWrapper.getExporter().getExporterProperties());
      }
      if (openTelemetryConfigWrapper.getSpanProcessorConfiguration() != null) {
        configMap.putAll(openTelemetryConfigWrapper.getSpanProcessorConfiguration().getConfigMap());
      }
      builder.addPropertiesSupplier(() -> Collections.unmodifiableMap(configMap));
      logger.debug("Creating OpenTelemetryConnection with properties: [" + configMap + "]");
    }
    builder.setServiceClassLoader(AutoConfiguredOpenTelemetrySdkBuilder.class.getClassLoader());
    openTelemetry = builder.build().getOpenTelemetrySdk();
    tracer = openTelemetry.getTracer(instrumentationName, instrumentationVersion);
    transactionStore = InMemoryTransactionStore.getInstance();
  }

  public static Optional<OpenTelemetryConnection> get() {
    return Optional.ofNullable(openTelemetryConnection);
  }

  public static synchronized OpenTelemetryConnection getInstance(
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    if (openTelemetryConnection == null) {
      openTelemetryConnection = new OpenTelemetryConnection(openTelemetryConfigWrapper);
    }
    return openTelemetryConnection;
  }

  /**
   * Load the module properties from filtered resource to get module related
   * information.
   * 
   * @return Properties
   */
  private static Properties getModuleProperties() {
    Properties moduleProperties = new Properties();
    try {
      InputStream resourceAsStream = OpenTelemetryConnection.class.getClassLoader()
          .getResourceAsStream("mule-opentelemetry-module.properties");
      if (resourceAsStream != null)
        moduleProperties.load(resourceAsStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return moduleProperties;
  }

  public SpanBuilder spanBuilder(String spanName) {
    return tracer.spanBuilder(spanName);
  }

  public <T> Context getTraceContext(T carrier, TextMapGetter<T> textMapGetter) {
    return openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), carrier, textMapGetter);
  }

  /**
   * Get the trace context information for a given transaction id. This returns
   * a {@link Map} with
   * at least one entry with key {@link TransactionStore#TRACE_TRANSACTION_ID} and
   * transactionId as value.
   * The other entries in the map depends on the propagator used.
   *
   * For W3C Trace Context Propagator, it can contain entries for `traceparent`
   * and optionally `tracestate`.
   *
   * @param transactionId
   *            Local transaction id
   * @return Map<String, String>
   */
  public Map<String, String> getTraceContext(String transactionId) {
    return getTraceContext(transactionId, (ComponentLocation) null);
  }

  /**
   * Get the trace context information for a given transaction id. This returns
   * a {@link Map} with
   * at least one entry with key {@link TransactionStore#TRACE_TRANSACTION_ID} and
   * transactionId as value.
   * The other entries in the map depends on the propagator used.
   * <p>
   * For W3C Trace Context Propagator, it can contain entries for `traceparent`
   * and optionally `tracestate`.
   *
   * @param transactionId
   *            Local transaction id
   * @param componentLocation
   *            {@link ComponentLocation} to get context for
   * @return Map<String, String>
   */
  public Map<String, String> getTraceContext(String transactionId, ComponentLocation componentLocation) {
    Context transactionContext = getTransactionStore().getTransactionContext(transactionId, componentLocation);
    Map<String, String> traceContext = new HashMap<>();
    traceContext.put(TransactionStore.TRACE_TRANSACTION_ID, transactionId);
    traceContext.put(TransactionStore.TRACE_ID, getTransactionStore().getTraceIdForTransaction(transactionId));
    try (Scope scope = transactionContext.makeCurrent()) {
      injectTraceContext(traceContext, HashMapTextMapSetter.INSTANCE);
    }
    logger.debug("Created trace context '{}' for TRACE_TRANSACTION_ID={}, Component Location '{}'", traceContext,
        transactionId,
        componentLocation);
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
