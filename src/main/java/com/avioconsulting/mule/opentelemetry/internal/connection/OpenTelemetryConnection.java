package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;
import com.avioconsulting.mule.opentelemetry.internal.config.CustomMetricInstrumentHolder;
import com.avioconsulting.mule.opentelemetry.api.config.metrics.MetricsInstrumentType;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.metrics.MetricsInstaller;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.store.*;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.message.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore.*;

public class OpenTelemetryConnection implements TraceContextHandler {

  public static final String TRACE_ID_LONG_LOW_PART = "traceIdLongLowPart";
  public static final String SPAN_ID_LONG = "spanIdLong";
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

  /**
   * Collect all otel specific system properties and cache them in a map.
   */
  public final Map<String, String> OTEL_SYSTEM_PROPERTIES_MAP = System.getProperties().stringPropertyNames().stream()
      .filter(p -> p.contains(".otel.")).collect(Collectors.toMap(String::toLowerCase, System::getProperty));

  private static final String INSTRUMENTATION_NAME = "mule-opentelemetry-module-DEV";
  private final TransactionStore transactionStore;
  private static OpenTelemetryConnection openTelemetryConnection;
  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;
  private final Meter meter;
  private boolean turnOffMetrics = false;
  private boolean turnOffTracing = false;
  private Map<String, CustomMetricInstrumentHolder<?>> metricInstruments;

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
      turnOffMetrics = openTelemetryConfigWrapper.isTurnOffMetrics();
      turnOffTracing = openTelemetryConfigWrapper.isTurnOffTracing();
    }
    builder.setServiceClassLoader(AutoConfiguredOpenTelemetrySdkBuilder.class.getClassLoader());
    builder.setResultAsGlobal();
    openTelemetry = builder.build().getOpenTelemetrySdk();
    tracer = openTelemetry.getTracer(instrumentationName, instrumentationVersion);
    meter = openTelemetry.meterBuilder(instrumentationName).setInstrumentationVersion(instrumentationVersion)
        .build();
    setupCustomMetrics(openTelemetryConfigWrapper);
    transactionStore = InMemoryTransactionStore.getInstance();
    PropertiesUtil.init();
  }

  private void setupCustomMetrics(OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    if (openTelemetryConfigWrapper == null || openTelemetryConfigWrapper.isTurnOffMetrics())
      return;
    Map<String, CustomMetricInstrumentHolder<?>> instruments = new HashMap<>();
    for (CustomMetricInstrumentDefinition customMetricInstrumentDefinition : openTelemetryConfigWrapper
        .getMetricInstrumentDefinitionMap().values()) {
      if (MetricsInstrumentType.COUNTER.equals(customMetricInstrumentDefinition.getInstrumentType())) {
        LongCounter counter = createCounter(customMetricInstrumentDefinition.getMetricName(),
            customMetricInstrumentDefinition.getDescription(),
            customMetricInstrumentDefinition.getUnit());
        instruments.put(customMetricInstrumentDefinition.getMetricName(),
            new CustomMetricInstrumentHolder<LongCounter>()
                .setInstrument(counter)
                .setMetricInstrument(customMetricInstrumentDefinition));
      }
    }
    metricInstruments = Collections.unmodifiableMap(instruments);
  }

  public boolean isTurnOffMetrics() {
    return turnOffMetrics;
  }

  public boolean isTurnOffTracing() {
    return turnOffTracing;
  }

  /**
   * {@link Supplier} to use with
   * {@link org.mule.runtime.api.connection.ConnectionProvider} where lazy
   * initialization is required.
   * 
   * @return a non-null {@code Supplier<OpenTelemetryConnection>}
   */
  public static Supplier<OpenTelemetryConnection> supplier() {
    return () -> openTelemetryConnection;
  }

  /**
   * This is for tests to reset the static instance in-between the tests.
   * Reset Global OpenTelemetry instances.
   */
  public static void resetForTest() {
    GlobalOpenTelemetry.resetForTest();
    GlobalEventEmitterProvider.resetForTest();
    openTelemetryConnection = null;
  }

  /**
   * Get Meter for provided metric name
   * 
   * @param name
   *            of the metric
   * @return CustomMetricInstrumentWrapper
   * @param <I>
   *            Instrument type such as LongCounter
   */
  @SuppressWarnings("unchecked")
  public <I> CustomMetricInstrumentHolder<I> getMetricInstrument(final String name) {
    return (CustomMetricInstrumentHolder<I>) metricInstruments.get(name);
  }

  public static synchronized OpenTelemetryConnection getInstance(
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    if (openTelemetryConnection == null) {
      openTelemetryConnection = new OpenTelemetryConnection(openTelemetryConfigWrapper);
      if (!openTelemetryConfigWrapper.isTurnOffMetrics()) {
        MetricsInstaller.install(openTelemetryConnection);
      }
    }
    return openTelemetryConnection;
  }

  public static synchronized OpenTelemetryConnection getInstance() {
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
    TransactionContext transactionContext = getTransactionStore().getTransactionContext(transactionId,
        componentLocation);
    Map<String, String> traceContext = new HashMap<>(10);
    traceContext.put(TRACE_TRANSACTION_ID, transactionId);
    traceContext.put(TRACE_ID, transactionContext.getTraceId());
    traceContext.put(TRACE_ID_LONG_LOW_PART, transactionContext.getTraceIdLongLowPart());
    traceContext.put(SPAN_ID, transactionContext.getSpanId());
    traceContext.put(SPAN_ID_LONG, transactionContext.getSpanIdLong());
    injectTraceContext(transactionContext.getContext(), traceContext,
        HashMapTextMapSetter.INSTANCE);
    logger.debug("Created trace context '{}' for TRACE_TRANSACTION_ID={}, Component Location '{}'", traceContext,
        transactionId,
        componentLocation);
    return traceContext;
  }

  public <T> void injectTraceContext(T carrier, TextMapSetter<T> textMapSetter) {
    openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), carrier, textMapSetter);
  }

  private <T> void injectTraceContext(Context context, T carrier, TextMapSetter<T> textMapSetter) {
    openTelemetry.getPropagators().getTextMapPropagator().inject(context, carrier, textMapSetter);
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

  /**
   * Creates a {@link Span} for given {@link TraceComponent} and adds it to the
   * {@link TraceComponent#getTransactionId()} transaction.
   * 
   * @param traceComponent
   *            {@link TraceComponent}
   * @param rootContainerName
   *            {@link String} name of the container such as flow that holds this
   *            component
   */
  public void addProcessorSpan(TraceComponent traceComponent, String rootContainerName) {
    SpanBuilder spanBuilder = this
        .spanBuilder(traceComponent.getSpanName())
        .setSpanKind(traceComponent.getSpanKind())
        .setStartTimestamp(traceComponent.getStartTime());
    OpenTelemetryUtil.addGlobalConfigSystemAttributes(
        traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey()),
        traceComponent.getTags(), OTEL_SYSTEM_PROPERTIES_MAP);
    traceComponent.getTags().forEach(spanBuilder::setAttribute);
    getTransactionStore().addProcessorSpan(
        rootContainerName,
        traceComponent, spanBuilder);
  }

  public SpanMeta endProcessorSpan(final TraceComponent traceComponent, Error error) {
    return getTransactionStore().endProcessorSpan(
        traceComponent.getTransactionId(),
        traceComponent.getLocation(),
        processorSpan -> {
          if (error != null) {
            processorSpan.getSpan().recordException(error.getCause());
          }
          setSpanStatus(traceComponent, processorSpan.getSpan());
          if (traceComponent.getTags() != null)
            traceComponent.getTags().forEach(processorSpan.getSpan()::setAttribute);
        },
        traceComponent.getEndTime());
  }

  public void startTransaction(TraceComponent traceComponent) {
    SpanBuilder spanBuilder = openTelemetryConnection
        .spanBuilder(traceComponent.getSpanName())
        .setSpanKind(traceComponent.getSpanKind())
        .setParent(traceComponent.getContext())
        .setStartTimestamp(traceComponent.getStartTime());

    OpenTelemetryUtil.addGlobalConfigSystemAttributes(
        traceComponent.getTags().get(SemanticAttributes.MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey()),
        traceComponent.getTags(), openTelemetryConnection.OTEL_SYSTEM_PROPERTIES_MAP);

    traceComponent.getTags().forEach(spanBuilder::setAttribute);
    getTransactionStore().startTransaction(
        traceComponent, traceComponent.getName(), spanBuilder);
  }

  public TransactionMeta endTransaction(final TraceComponent traceComponent, Exception exception) {
    if (traceComponent == null) {
      return null;
    }
    return openTelemetryConnection.getTransactionStore().endTransaction(
        traceComponent.getTransactionId(),
        traceComponent.getName(),
        rootSpan -> {
          traceComponent.getTags().forEach(rootSpan::setAttribute);
          openTelemetryConnection.setSpanStatus(traceComponent, rootSpan);
          if (exception != null) {
            rootSpan.recordException(exception);
          }
        },
        traceComponent.getEndTime());
  }

  public void setSpanStatus(TraceComponent traceComponent, Span span) {
    if (traceComponent.getStatusCode() != null
        && !StatusCode.UNSET.equals(traceComponent.getStatusCode())) {
      span.setStatus(traceComponent.getStatusCode(), traceComponent.getErrorMessage());
    }
  }

  public LongCounter createCounter(String metricName, String description, String unit) {
    logger.trace("Creating counter for metric {}", metricName);
    return meter.counterBuilder(metricName)
        .setDescription(description)
        .setUnit(unit)
        .build();
  }

  public LongCounter createCounter(String metricName, String description) {
    return createCounter(metricName, description, "1");
  }

  public DoubleHistogram createHistogram(String metricName, String description) {
    logger.trace("Creating histogram for metric {}", metricName);
    return meter.histogramBuilder(metricName)
        .setDescription(description)
        .setUnit("ms") // Time in millis
        .build();
  }

  public List<AutoCloseable> registerMetricsObserver(Function<OpenTelemetry, List<AutoCloseable>> observer) {
    return observer.apply(openTelemetry);
  }

  public void registerMetricsObserver(Consumer<OpenTelemetry> observer) {
    observer.accept(openTelemetry);
  }
}
