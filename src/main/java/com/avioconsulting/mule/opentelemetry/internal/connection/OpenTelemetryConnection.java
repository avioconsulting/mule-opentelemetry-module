package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsProvider;
import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.traces.TransactionContext;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.ServiceProviderUtil;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.ERROR_TYPE;
import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;

public class OpenTelemetryConnection implements TraceContextHandler {

  public static final String TRACE_ID_LONG_LOW_PART = "traceIdLongLowPart";
  public static final String SPAN_ID_LONG = "spanIdLong";
  private final OpenTelemetryMetricsProviderCollection metricsProviders = ServiceProviderUtil
      .load(OpenTelemetryMetricsProvider.class.getClassLoader(), OpenTelemetryMetricsProvider.class,
          new OpenTelemetryMetricsProviderCollection());
  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryConnection.class);
  private OpenTelemetryMetricsConfigProvider metricsProvider;
  private AppIdentifier appIdentifier;
  private ExpressionManager expressionManager;
  /**
   * Instrumentation version must be picked from the module's artifact version.
   * This is a fallback for any dev testing.
   */
  private static final String INSTRUMENTATION_VERSION = "0.0.1-DEV";

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
  private boolean turnOffTracing = false;
  private boolean turnOffMetrics = false;

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
      turnOffTracing = openTelemetryConfigWrapper.isTurnOffTracing();
      turnOffMetrics = openTelemetryConfigWrapper.isTurnOffMetrics();
      appIdentifier = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getAppIdentifier();
      metricsProvider = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getMetricsConfigProvider();
      expressionManager = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getExpressionManager();
    }
    builder.setServiceClassLoader(AutoConfiguredOpenTelemetrySdkBuilder.class.getClassLoader());
    builder.setResultAsGlobal();
    if (!turnOffMetrics)
      metricsProvider.initialise(appIdentifier);
    openTelemetry = builder.build().getOpenTelemetrySdk();
    installOpenTelemetryLogger();
    if (!turnOffMetrics) {
      logger.info("Initializing Metrics Providers");
      metricsProvider.start();
      metricsProviders.initialize(metricsProvider, openTelemetry);
    } else {
      logger.info("Disabling loaded Metrics Providers");
      metricsProviders.clear();
    }
    tracer = openTelemetry.getTracer(instrumentationName, instrumentationVersion);
    transactionStore = InMemoryTransactionStore.getInstance();
    PropertiesUtil.init();
  }

  private void installOpenTelemetryLogger() {
    try {
      Class<?> clazz = Class
          .forName("com.avioconsulting.mule.opentelemetry.logs.api.OpenTelemetryLog4jAppender");
      Method install = clazz.getMethod("install", OpenTelemetry.class);
      logger.info("Initializing AVIO OpenTelemetry Log4J support");
      install.invoke(null, openTelemetry);
    } catch (ClassNotFoundException e) {
      logger.warn(
          "OpenTelemetry Log4j support not found on the classpath. Logs will not be exported via OpenTelemetry.");
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public AppIdentifier getAppIdentifier() {
    return appIdentifier;
  }

  public boolean isTurnOffTracing() {
    return turnOffTracing;
  }

  public boolean isTurnOffMetrics() {
    return turnOffMetrics;
  }

  public OpenTelemetryMetricsProviderCollection getMetricsProviders() {
    return metricsProviders;
  }

  /**
   * {@link Supplier} to use with
   * {@link ConnectionProvider} where lazy
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
    if (openTelemetryConnection != null && openTelemetryConnection.metricsProvider != null) {
      openTelemetryConnection.metricsProvider.stop();
      openTelemetryConnection.getMetricsProviders().stop();
    }
    GlobalOpenTelemetry.resetForTest();
    GlobalEventEmitterProvider.resetForTest();
    openTelemetryConnection = null;
  }

  public static synchronized OpenTelemetryConnection getInstance(
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    if (openTelemetryConnection == null) {
      openTelemetryConnection = new OpenTelemetryConnection(openTelemetryConfigWrapper);
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
    return getTraceContext(transactionId, (String) null);
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
  public Map<String, String> getTraceContext(String transactionId, String componentLocation) {
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

  public Meter get(String instrumentationScopeName) {
    return openTelemetry.meterBuilder(instrumentationScopeName).build();
  }

  public ExpressionManager getExpressionManager() {
    return expressionManager;
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
   * @param containerName
   *            {@link String} name of the container such as flow that holds this
   *            component
   */
  public void addProcessorSpan(TraceComponent traceComponent, String containerName) {
    SpanBuilder spanBuilder = this
        .spanBuilder(traceComponent.getSpanName())
        .setSpanKind(traceComponent.getSpanKind())
        .setStartTimestamp(traceComponent.getStartTime());
    OpenTelemetryUtil.addGlobalConfigSystemAttributes(
        traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey()),
        traceComponent.getTags(), OTEL_SYSTEM_PROPERTIES_MAP);
    traceComponent.getTags().forEach(spanBuilder::setAttribute);

    String parentLocation = getRouteContainerLocation(traceComponent);
    if (parentLocation != null) {
      // Create parent span for the first processor in the chain /0
      TraceComponent parentTrace = TraceComponent.of(parentLocation)
          .withLocation(parentLocation)
          .withTags(Collections.emptyMap())
          .withTransactionId(traceComponent.getTransactionId())
          .withSpanName(parentLocation)
          .withSpanKind(SpanKind.INTERNAL)
          .withEventContextId(traceComponent.getEventContextId())
          .withStartTime(traceComponent.getStartTime());
      // if (!getTransactionStore().processorSpanExists(traceComponent)) {
      SpanMeta parentSpan = addRouteSpan(parentTrace, traceComponent, parentLocation,
          getLocationParent(parentLocation));
      spanBuilder.setParent(parentSpan.getContext());
      // }
    }
    if (parentLocation == null) {
      parentLocation = containerName;
    }
    getTransactionStore().addProcessorSpan(
        parentLocation,
        traceComponent, spanBuilder);
  }

  private SpanMeta addRouteSpan(TraceComponent parentTrace, TraceComponent childTrace, String parentLocation,
      String rootContainerName) {
    SpanBuilder spanBuilder = this.spanBuilder(parentLocation)
        .setParent(childTrace.getContext())
        .setSpanKind(SpanKind.INTERNAL)
        .setStartTimestamp(childTrace.getStartTime());
    return getTransactionStore().addProcessorSpan(
        rootContainerName,
        parentTrace, spanBuilder);
  }

  public SpanMeta endProcessorSpan(final TraceComponent traceComponent, Error error) {
    return getTransactionStore().endProcessorSpan(
        traceComponent.getTransactionId(),
        traceComponent,
        span -> {
          if (error != null) {
            span.recordException(error.getCause());
          }
          setSpanStatus(traceComponent, span);
          if (traceComponent.getTags() != null)
            traceComponent.getTags().forEach(span::setAttribute);
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
        traceComponent,
        rootSpan -> {
          traceComponent.getTags().forEach(rootSpan::setAttribute);
          openTelemetryConnection.setSpanStatus(traceComponent, rootSpan);
          if (exception != null) {
            rootSpan.recordException(exception);
            rootSpan.setAttribute(ERROR_TYPE.getKey(), exception.getClass().getTypeName());
          }
        });
  }

  public void setSpanStatus(TraceComponent traceComponent, Span span) {
    if (traceComponent.getStatusCode() != null
        && !StatusCode.UNSET.equals(traceComponent.getStatusCode())) {
      span.setStatus(traceComponent.getStatusCode(), traceComponent.getErrorMessage());
    }
  }

}
