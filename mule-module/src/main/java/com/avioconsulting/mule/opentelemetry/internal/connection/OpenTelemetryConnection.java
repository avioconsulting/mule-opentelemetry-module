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
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.ServiceProviderUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.BiFunctionMemoizer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.semconv.ErrorAttributes;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.hasBatchJobInstanceId;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.copyBatchTags;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.tagsToAttributes;

public class OpenTelemetryConnection implements TraceContextHandler,
    com.avioconsulting.mule.opentelemetry.internal.store.TransactionProcessor {

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

  private static final String INSTRUMENTATION_NAME = "mule-opentelemetry-module-DEV";
  private final TransactionStore transactionStore;
  private OpenTelemetry openTelemetry;
  private final Tracer tracer;
  private boolean turnOffTracing = false;
  private boolean turnOffMetrics = false;
  private ComponentRegistryService componentRegistryService;
  private final BiFunctionMemoizer<String, TraceComponent, String> parentLocationMemoizer = BiFunctionMemoizer
      .memoize((key, traceComponent) -> getRouteContainerLocation(traceComponent), true);

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
      // Disable the resource providers that are handled by
      // MuleAppHostResourceProvider
      configMap.put("otel.java.disabled.resource.providers",
          "io.opentelemetry.instrumentation.resources.HostResourceProvider,io.opentelemetry.instrumentation.resources.ContainerResourceProvider");
      builder.addPropertiesSupplier(() -> Collections.unmodifiableMap(configMap));
      logger.debug("Creating OpenTelemetryConnection with properties: [{}]", configMap);
      turnOffTracing = openTelemetryConfigWrapper.isTurnOffTracing();
      turnOffMetrics = openTelemetryConfigWrapper.isTurnOffMetrics();
      appIdentifier = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getAppIdentifier();
      metricsProvider = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getMetricsConfigProvider();
      expressionManager = openTelemetryConfigWrapper.getOpenTelemetryConfiguration().getExpressionManager();
    }
    builder.setServiceClassLoader(AutoConfiguredOpenTelemetrySdkBuilder.class.getClassLoader());
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
    transactionStore = InMemoryTransactionStore.getInstance(this);
    PropertiesUtil.init();
  }

  public OpenTelemetryConnection setComponentRegistryService(ComponentRegistryService componentRegistryService) {
    this.componentRegistryService = componentRegistryService;
    return this;
  }

  @Override
  public ComponentRegistryService getComponentRegistryService() {
    return componentRegistryService;
  }

  // For testing purpose only
  public void withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
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
   * This is for tests to reset the static instance in-between the tests.
   * Reset Global OpenTelemetry instances.
   */
  public static void _resetForTest() {
    InMemoryTransactionStore._resetForTesting();
  }

  public static synchronized OpenTelemetryConnection getInstance(
      OpenTelemetryConfigWrapper openTelemetryConfigWrapper) {
    return new OpenTelemetryConnection(openTelemetryConfigWrapper);
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
  public Map<String, Object> getTraceContext(String transactionId) {
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
  public Map<String, Object> getTraceContext(String transactionId, String componentLocation) {
    TransactionContext transactionContext = getTransactionStore().getTransactionContext(transactionId,
        componentLocation);
    Map<String, Object> traceContext = transactionContext.getTraceContextMap();
    injectTraceContext(transactionContext.getContext(), traceContext,
        HashMapObjectMapSetter.INSTANCE);
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

  public static enum HashMapObjectMapSetter implements TextMapSetter<Map<String, Object>> {
    INSTANCE;

    @Override
    public void set(@Nullable Map<String, Object> carrier, String key, String value) {
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
  @Override
  public void addProcessorSpan(TraceComponent traceComponent, String containerName) {
    SpanBuilder spanBuilder = this
        .spanBuilder(traceComponent.getSpanName())
        .setSpanKind(traceComponent.getSpanKind())
        .setStartTimestamp(traceComponent.getStartTime());
    tagsToAttributes(traceComponent, spanBuilder);
    String parentLocation = null;
    if (!hasBatchJobInstanceId(traceComponent) ||
        !BatchHelperUtil.notBatchChildContainer(containerName, componentRegistryService)) {
      // When processing batch child containers are
      // handled by BatchTransaction, skip this
      parentLocation = parentLocationMemoizer.apply(traceComponent.getLocation(), traceComponent);
      if (parentLocation != null) {
        // Create parent span for the first processor in the chain /0
        TraceComponent parentTrace = TraceComponent.of(parentLocation)
            .withLocation(parentLocation)
            .withTags(new HashMap<>())
            .withTransactionId(traceComponent.getTransactionId())
            .withSpanName(parentLocation)
            .withSpanKind(SpanKind.INTERNAL)
            .withEventContextId(traceComponent.getEventContextId())
            .withStartTime(traceComponent.getStartTime());
        copyBatchTags(traceComponent, parentTrace);
        SpanMeta parentSpan = addRouteSpan(parentTrace, traceComponent, parentLocation,
            getLocationParent(parentLocation));
        spanBuilder.setParent(parentSpan.getContext());
      }
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

  @Override
  public SpanMeta endProcessorSpan(final TraceComponent traceComponent, Error error) {
    SpanMeta spanMeta = getTransactionStore().endProcessorSpan(
        traceComponent.getTransactionId(),
        traceComponent,
        span -> {
          if (error != null) {
            span.recordException(error.getCause());
            traceComponent.getTags().put(ERROR_TYPE.getKey(),
                error.getCause().getClass().getCanonicalName());
            traceComponent.getTags().put(ERROR_MESSAGE.getKey(),
                error.getDescription());
            if (error.getErrorType() != null
                && !MULE_ANY.equals(error.getErrorType().toString())) {
              traceComponent.getTags().put(ErrorAttributes.ERROR_TYPE.getKey(),
                  error.getErrorType().toString());
            }
          }
          setSpanStatus(traceComponent, span);
          tagsToAttributes(traceComponent, span);
        },
        traceComponent.getEndTime());
    processBatchJob(spanMeta, traceComponent);
    return spanMeta;
  }

  private void processBatchJob(SpanMeta spanMeta, TraceComponent traceComponent) {
    if (!traceComponent.getName().equalsIgnoreCase(BATCH_JOB_TAG)) {
      return;
    }
    String batchJobInstanceId = traceComponent.getTags().get(MULE_BATCH_JOB_INSTANCE_ID.getKey());
    Map<String, String> tags = new HashMap<>(10);
    tags.putAll(spanMeta.getTags());
    tags.putAll(traceComponent.getTags());
    TraceComponent batchComponent = TraceComponent
        .of(traceComponent.getTags().get(MULE_BATCH_JOB_NAME.getKey()), traceComponent.getComponentLocation())
        .withLocation(traceComponent.getLocation())
        .withTags(tags)
        .withTransactionId(batchJobInstanceId)
        .withSpanName(traceComponent.getTags().get(MULE_BATCH_JOB_NAME.getKey()))
        .withSpanKind(SpanKind.SERVER)
        .withContext(spanMeta.getContext())
        .withEventContextId(traceComponent.getEventContextId())
        .withStartTime(traceComponent.getStartTime());
    startTransaction(batchComponent);
    if (BatchHelperUtil.isBatchSupportDisabled()) {
      // The Batch Job transaction is just for representation since batch support is
      // disabled
      // End the transaction.
      batchComponent.getTags().put(MULE_BATCH_TRACE_DISABLED.getKey(), "true");
      batchComponent.withEndTime(traceComponent.getEndTime());
      endTransaction(batchComponent, null);
    }
  }

  @Override
  public void startTransaction(TraceComponent traceComponent) {
    SpanBuilder spanBuilder = this
        .spanBuilder(traceComponent.getSpanName())
        .setSpanKind(traceComponent.getSpanKind())
        .setParent(traceComponent.getContext())
        .setStartTimestamp(traceComponent.getStartTime());
    String configName;
    if ((configName = traceComponent.getTags().get(MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey())) != null) {
      traceComponent.getTags().putAll(componentRegistryService.getGlobalConfigOtelSystemProperties(configName));
    }
    getTransactionStore().startTransaction(
        traceComponent, traceComponent.getName(), spanBuilder);
  }

  @Override
  public TransactionMeta endTransaction(final TraceComponent traceComponent, Exception exception) {
    if (traceComponent == null) {
      return null;
    }
    return getTransactionStore().endTransaction(
        traceComponent,
        rootSpan -> {
          tagsToAttributes(traceComponent, rootSpan);
          setSpanStatus(traceComponent, rootSpan);
          if (exception != null) {
            rootSpan.recordException(exception);
            rootSpan.setAttribute(ERROR_TYPE, exception.getClass().getCanonicalName());
            rootSpan.setAttribute(ERROR_MESSAGE, exception.getMessage());
            if (exception instanceof MuleException) {
              MuleException muleException = (MuleException) exception;
              if (muleException.getExceptionInfo() != null
                  && muleException.getExceptionInfo().getErrorType() != null
                  && !MULE_ANY.equals(muleException.getExceptionInfo().getErrorType().toString())) {
                rootSpan.setAttribute(ERROR_TYPE,
                    muleException.getExceptionInfo().getErrorType().toString());
              }
            }
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
