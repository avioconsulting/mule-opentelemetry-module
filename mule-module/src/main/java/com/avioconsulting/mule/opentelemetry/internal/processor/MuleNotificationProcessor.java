package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.*;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotification;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig;
import com.avioconsulting.mule.opentelemetry.internal.notifications.BatchError;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.AsyncMessageNotification;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.api.util.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.addBatchTags;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.*;

/**
 * Notification Processor bean. This is injected through registry-bootstrap into
 * Extension configuration,
 * see
 * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
 */
public class MuleNotificationProcessor {

  private static final Logger logger = LoggerFactory.getLogger(MuleNotificationProcessor.class);
  public static final String MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME = "mule.otel.span.processors.enable";
  public static final List<String> CONTEXT_EXPRESSIONS = Arrays.asList("#[attributes.headers]",
      "#[attributes.properties]",
      "#[attributes.properties.userProperties]", "#[payload.message.messageAttributes]");

  private Supplier<OpenTelemetryConnection> connectionSupplier;
  private boolean spanAllProcessors;
  private TraceLevelConfiguration traceLevelConfiguration;
  private OpenTelemetryConnection openTelemetryConnection;

  ConfigurationComponentLocator configurationComponentLocator;
  private ProcessorComponentService processorComponentService;
  private final ProcessorComponent flowProcessorComponent;
  private final InterceptorProcessorConfig interceptorProcessorConfig;
  /**
   * Cache the context expressions for flows to avoid trial-and-error every time
   */
  private final ConcurrentHashMap<String, String> flowContextExpressions = new ConcurrentHashMap<>();

  /**
   * This {@link GenericProcessorComponent} will be used for processors that do
   * not have a specific processor like {@link HttpProcessorComponent}.
   */
  private final ProcessorComponent genericProcessorComponent;

  @Inject
  public MuleNotificationProcessor(ConfigurationComponentLocator configurationComponentLocator) {
    this.configurationComponentLocator = configurationComponentLocator;
    flowProcessorComponent = new FlowProcessorComponent()
        .withConfigurationComponentLocator(configurationComponentLocator);
    genericProcessorComponent = new GenericProcessorComponent()
        .withConfigurationComponentLocator(configurationComponentLocator);
    interceptorProcessorConfig = new InterceptorProcessorConfig()
        .setComponentLocator(configurationComponentLocator);
  }

  public InterceptorProcessorConfig getInterceptorProcessorConfig() {
    return interceptorProcessorConfig;
  }

  public boolean hasConnection() {
    return openTelemetryConnection != null;
  }

  public OpenTelemetryConnection getOpenTelemetryConnection() {
    return openTelemetryConnection;
  }

  public Supplier<OpenTelemetryConnection> getConnectionSupplier() {
    return connectionSupplier;
  }

  public ConfigurationComponentLocator getConfigurationComponentLocator() {
    return configurationComponentLocator;
  }

  public void init(OpenTelemetryConnection connection,
      TraceLevelConfiguration traceLevelConfiguration) {
    this.openTelemetryConnection = connection;
    this.spanAllProcessors = Boolean.parseBoolean(System.getProperty(MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME,
        Boolean.toString(traceLevelConfiguration.isSpanAllProcessors())));
    this.traceLevelConfiguration = traceLevelConfiguration;
    interceptorProcessorConfig
        .setTurnOffTracing(openTelemetryConnection.isTurnOffTracing())
        .updateTraceConfiguration(traceLevelConfiguration);

    processorComponentService = ProcessorComponentService.getInstance();
  }

  public void handleProcessorStartEvent(MessageProcessorNotification notification) {
    String location = notification.getComponent().getLocation().getLocation();
    if (ComponentsUtil.isAsyncScope(notification.getComponent().getLocation().getComponentIdentifier())) {
      // Async scopes are handled via AsyncMessageNotifications.
      // Creating one here will create duplicate spans
      return;
    }
    if (interceptorProcessorConfig.shouldIntercept(notification.getComponent().getLocation(),
        notification.getEvent())) {
      logger.trace(
          "Component {} will be processed by interceptor, skipping notification processing to create span",
          location);
      return;
    }
    processComponentStartSpan(notification);
  }

  /**
   * Process the {@link AsyncMessageNotification} to capture start of the span.
   * 
   * @param notification
   *            AsyncMessageNotification
   */
  public void handleAsyncScheduledEvent(AsyncMessageNotification notification) {
    processComponentStartSpan(notification);
  }

  /**
   * A common and generic start of the span based on
   * {@link EnrichedServerNotification}.
   * 
   * @param notification
   *            {@link EnrichedServerNotification}
   */
  private void processComponentStartSpan(EnrichedServerNotification notification) {
    try {
      ProcessorComponent processorComponent = getProcessorComponent(notification.getComponent().getIdentifier());
      if (processorComponent != null) {
        logger.trace("Handling '{}:{}' processor start event context id {} correlation id {} ",
            notification.getResourceIdentifier(), notification.getComponent().getIdentifier(),
            notification.getEvent().getContext().getId(),
            notification.getEvent().getCorrelationId());
        TraceComponent traceComponent = processorComponent.getStartTraceComponent(notification)
            .withStartTime(Instant.ofEpochMilli(notification.getTimestamp()))
            .withEventContextId(notification.getEvent().getContext().getId())
            .withComponentLocation(notification.getComponent().getLocation());
        addBatchTags(traceComponent, notification.getEvent());
        resolveExpressions(traceComponent, openTelemetryConnection.getExpressionManager(),
            notification.getEvent());
        long siblings = configurationComponentLocator.findAllLocations().stream()
            .filter(c -> c.getLocation()
                .startsWith(ComponentsUtil.getLocationParent(
                    notification.getComponent().getLocation().getLocation()) + "/"))
            .count();
        traceComponent.withSiblings(siblings);
        openTelemetryConnection.addProcessorSpan(traceComponent,
            ComponentsUtil.getLocationParent(notification.getComponent().getLocation().getLocation()));
        processFlowRef(traceComponent, notification.getEvent());
      }
    } catch (Exception ex) {
      logger.trace(
          "Failed to intercept processor {} at {}, span may not be captured for this processor. Error - {}",
          notification.getComponent().getIdentifier().toString(),
          notification.getComponent().getLocation().getLocation(),
          ex.getLocalizedMessage(), ex);
    }
  }

  private void processFlowRef(TraceComponent traceComponent, Event event) {
    if (isFlowRef(traceComponent.getComponentLocation())) {
      Optional<ComponentLocation> subFlowLocation = resolveFlowName(
          getOpenTelemetryConnection().getExpressionManager(), traceComponent, event.asBindingContext(),
          configurationComponentLocator);
      if (subFlowLocation.isPresent()) {
        TraceComponent subflowTrace = getSubFlowTraceComponent(subFlowLocation.get(), traceComponent);
        getOpenTelemetryConnection().addProcessorSpan(subflowTrace,
            traceComponent.getComponentLocation().getLocation());
      }
    }
  }

  /**
   * <pre>
   * Finds a {@link ProcessorComponent} for {@link org.mule.runtime.api.component.Component} that caused {@link MessageProcessorNotification} event.
   *
   * If `spanAllProcessors` is set to <code>true</code> but the target component is marked to ignore spans, no processor will be returned.
   *
   * If a specific processor isn't found and `spanAllProcessors` is <code>true</code> then {@link GenericProcessorComponent} will be returned to process target component.
   *
   * </pre>
   * 
   * @param notification
   *            {@link MessageProcessorNotification} instance containing the
   *            target {@link org.mule.runtime.api.component.Component}.
   * @return Optional<ProcessorComponent> that can process this notification
   */
  ProcessorComponent getProcessorComponent(MessageProcessorNotification notification) {
    ComponentIdentifier identifier = notification.getComponent().getIdentifier();
    return getProcessorComponent(identifier);
  }

  public ProcessorComponent getProcessorComponent(ComponentIdentifier identifier) {
    boolean ignored = multiMapContains(identifier.getNamespace(), identifier.getName(), "*",
        traceLevelConfiguration.getIgnoreMuleComponentsMap());
    if (spanAllProcessors && ignored)
      return null;

    ProcessorComponent processorComponent = processorComponentService
        .getProcessorComponentFor(identifier, configurationComponentLocator,
            openTelemetryConnection.getExpressionManager());

    if (processorComponent == null && (spanAllProcessors
        || multiMapContains(identifier.getNamespace(), identifier.getName(), "*",
            traceLevelConfiguration.getSpanAdditionalMuleComponentsMap()))) {
      processorComponent = genericProcessorComponent;
    }
    return processorComponent;
  }

  private boolean multiMapContains(String key, String value, String alternate, MultiMap<String, String> multiMap) {
    List<String> values = multiMap.getAll(key);
    return values.contains(value) || values.contains(alternate);
  }

  public void handleProcessorEndEvent(EnrichedServerNotification notification) {
    String location = notification.getComponent().getLocation().getLocation();
    try {
      ProcessorComponent processorComponent = getProcessorComponent(notification.getComponent().getIdentifier());
      if (processorComponent != null) {
        logger.trace("Handling '{}:{}' processor end event context id {} correlation id {} ",
            notification.getResourceIdentifier(), notification.getComponent().getIdentifier(),
            notification.getEvent().getContext().getId(),
            notification.getEvent().getCorrelationId());
        TraceComponent traceComponent = processorComponent.getEndTraceComponent(notification)
            .withEndTime(Instant.ofEpochMilli(notification.getTimestamp()))
            .withEventContextId(notification.getEvent().getContext().getId());
        addBatchTags(traceComponent, notification.getEvent());
        SpanMeta spanMeta = openTelemetryConnection.endProcessorSpan(traceComponent,
            notification.getEvent().getError().orElse(null));

        if (isFlowRef(notification.getComponent().getLocation())) {
          String targetFlowName = traceComponent.getTags().get("mule.app.processor.flowRef.name");
          if (openTelemetryConnection.getExpressionManager().isExpression(targetFlowName)) {
            logger.trace("Resolving expression '{}'", targetFlowName);
            targetFlowName = openTelemetryConnection.getExpressionManager()
                .evaluate(targetFlowName, notification.getEvent().asBindingContext()).getValue()
                .toString();
            logger.trace("Resolved to value '{}'", targetFlowName);
          }
          findLocation(targetFlowName,
              configurationComponentLocator)
                  .filter(ComponentsUtil::isSubFlow)
                  .ifPresent(subFlowComp -> {
                    TraceComponent subflowTrace = getSubFlowTraceComponent(subFlowComp,
                        traceComponent);
                    SpanMeta subFlow = openTelemetryConnection.endProcessorSpan(subflowTrace,
                        notification.getEvent().getError().orElse(null));
                    if (subFlow != null) {
                      openTelemetryConnection.getMetricsProviders().captureProcessorMetrics(
                          notification.getComponent(),
                          notification.getEvent().getError().orElse(null), location,
                          spanMeta);
                    }
                  });
        }

        if (spanMeta != null) {
          openTelemetryConnection.getMetricsProviders().captureProcessorMetrics(notification.getComponent(),
              notification.getEvent().getError().orElse(null), location, spanMeta);
        }
      }
    } catch (Exception ex) {
      logger.error("Error in handling processor end event", ex);
      throw ex;
    }
  }

  public void handleFlowStartEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow start event context id {} correlation id {} ",
          notification.getResourceIdentifier(), notification.getEvent().getContext().getId(),
          notification.getEvent().getCorrelationId());
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceStartTraceComponent(notification, openTelemetryConnection)
          .withStartTime(Instant.ofEpochMilli(notification.getTimestamp()))
          .withEventContextId(notification.getEvent().getContext().getId());
      traceComponent = attemptAddingTraceContextIfMissing(notification, traceComponent);
      addBatchTags(traceComponent, notification.getEvent());
      openTelemetryConnection.startTransaction(traceComponent);
    } catch (Exception ex) {
      logger.error("Error in handling {} flow start event", notification.getResourceIdentifier(), ex);
      throw ex;
    }
  }

  private TraceComponent attemptAddingTraceContextIfMissing(PipelineMessageNotification notification,
      TraceComponent traceComponent) {
    if (traceComponent.getContext() != null) {
      return traceComponent;
    }
    if (flowContextExpressions.containsKey(notification.getResourceIdentifier())) {
      String expression = flowContextExpressions.get(notification.getResourceIdentifier());
      logger.trace("Getting context for {} with {}", notification.getResourceIdentifier(), expression);
      Context context = getContext(expression, notification);
      traceComponent = traceComponent.withContext(context);
    } else {
      for (String expression : CONTEXT_EXPRESSIONS) {
        if (traceComponent.getContext() == null) {
          try {
            Context context = getContext(expression, notification);
            if (context != null) {
              traceComponent = traceComponent.withContext(context);
              logger.info("Got context for {} with {}, adding to cache",
                  notification.getResourceIdentifier(), expression);
              flowContextExpressions.put(notification.getResourceIdentifier(), expression);
              break;
            }
          } catch (Exception ignored) {
          }
        }
      }
    }
    return traceComponent;
  }

  private Context getContext(String expression, EnrichedServerNotification notification) {
    try {
      TypedValue<?> contextCarrier = openTelemetryConnection
          .getExpressionManager().evaluate(
              expression,
              notification.getEvent().asBindingContext());
      if (contextCarrier.getValue() != null && contextCarrier.getValue() instanceof Map) {
        Map<String, Object> resolved = new HashMap<>(((Map) contextCarrier.getValue()).size());
        ((Map) contextCarrier.getValue()).forEach((key, value) -> {
          if (value instanceof byte[]) {
            // Some modules like Kafka serialize values in byte[]
            resolved.put(key.toString(), new String((byte[]) value));
          } else {
            resolved.put(key.toString(), value.toString());
          }
        });
        return openTelemetryConnection.getTraceContext(resolved,
            AbstractProcessorComponent.ContextMapGetter.INSTANCE);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  public void handleFlowEndEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow end event context id {} correlation id {} ",
          notification.getResourceIdentifier(), notification.getEvent().getContext().getId(),
          notification.getEvent().getCorrelationId());
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceEndTraceComponent(notification, openTelemetryConnection)
          .withEndTime(Instant.ofEpochMilli(notification.getTimestamp()))
          .withEventContextId(notification.getEvent().getContext().getId());
      addBatchTags(traceComponent, notification.getEvent());
      TransactionMeta transactionMeta = openTelemetryConnection.endTransaction(traceComponent,
          notification.getException());
      if (transactionMeta == null) {
        // If transaction isn't found by the current context,
        // search by any context from variable
        TypedValue<String> contextId = (TypedValue<String>) notification.getEvent().getVariables()
            .get(TransactionStore.OTEL_FLOW_CONTEXT_ID);
        if (contextId != null && contextId.getValue() != null) {
          logger.trace("Attempting to find {} by {}", traceComponent, contextId.getValue());
          traceComponent = traceComponent.withEventContextId(contextId.getValue());
        }
        transactionMeta = openTelemetryConnection.endTransaction(traceComponent,
            notification.getException());
      }
      if (transactionMeta != null) {
        openTelemetryConnection.getMetricsProviders().captureFlowMetrics(transactionMeta,
            notification.getResourceIdentifier(),
            notification.getException());
      }

    } catch (Exception ex) {
      logger.error("Error in handling {} flow end event", notification.getResourceIdentifier(), ex);
      throw ex;
    }
  }

  public void handleBatchOnCompleteEndEvent(OtelBatchNotification batchNotification) {
    BatchJobInstance jobInstance = batchNotification.getJobInstance();
    TraceComponent traceComponent = TraceComponent.of(BATCH_ON_COMPLETE_TAG)
        .withSpanName(BATCH_ON_COMPLETE_TAG)
        .withTransactionId(jobInstance.getId())
        .withEndTime(Instant.ofEpochMilli(batchNotification.getTimestamp()))
        .withTags(new HashMap<>());
    traceComponent.getTags().put(MULE_BATCH_JOB_INSTANCE_ID.toString(), jobInstance.getId());
    openTelemetryConnection.endProcessorSpan(traceComponent,
        BatchError.of(batchNotification.getException()));
    if (BatchJobInstanceStatus.FAILED_PROCESS_RECORDS.equals(batchNotification.getJobInstance().getStatus())) {
      // When batch job ends due exceeding the number of allowed records to fail,
      // JOB_SUCCESSFUL or JOB_STOPPED notification isn't fired.
      // So, when the on-complete block finished with this status, also end the batch
      // job.
      handleBatchEndEvent(batchNotification);
    }
  }

  public void handleBatchEndEvent(OtelBatchNotification batchNotification) {
    BatchJobInstance jobInstance = batchNotification.getJobInstance();
    TraceComponent traceComponent = TraceComponent.of(batchNotification.getJobInstance().getOwnerJobName())
        .withTransactionId(jobInstance.getId())
        .withEndTime(Instant.ofEpochMilli(batchNotification.getTimestamp()))
        .withTags(new HashMap<>());

    openTelemetryConnection.endTransaction(traceComponent, batchNotification.getException());
  }

  public void handleBatchStepRecordEndEvent(OtelBatchNotification batchNotification) {
    BatchJobInstance jobInstance = batchNotification.getJobInstance();
    BatchStep batchStep = batchNotification.getStep();
    Record record = batchNotification.getRecord();
    String recordLocation = batchNotification.getStep().getComponent().getLocation()
        .getLocation();
    recordLocation = recordLocation + "/record";
    TraceComponent traceComponent = TraceComponent.of(BATCH_STEP_RECORD_TAG)
        .withLocation(recordLocation)
        .withSpanName(BATCH_STEP_RECORD_TAG)
        .withTransactionId(jobInstance.getId())
        .withEndTime(Instant.ofEpochMilli(batchNotification.getTimestamp()))
        .withEventContextId(
            record.getVariable(TransactionStore.OTEL_BATCH_STEP_RECORD_CONTEXT_ID).getValue().toString())
        .withTags(new HashMap<>());
    traceComponent.getTags().put(MULE_BATCH_JOB_INSTANCE_ID.getKey(), jobInstance.getId());
    traceComponent.getTags().put(MULE_BATCH_JOB_NAME.getKey(), jobInstance.getOwnerJobName());
    traceComponent.getTags().put(MULE_BATCH_JOB_STEP_NAME.getKey(), batchStep.getName());

    openTelemetryConnection.endProcessorSpan(traceComponent,
        BatchError.of(record.getExceptionForStep(record.getCurrentStepId())));
  }

  public void handleBatchStepEndEvent(OtelBatchNotification batchNotification) {
    BatchJobInstance jobInstance = batchNotification.getJobInstance();
    BatchStep batchStep = batchNotification.getStep();
    TraceComponent traceComponent = TraceComponent.of(BATCH_STEP_TAG)
        .withSpanName(batchStep.getName())
        .withTransactionId(jobInstance.getId())
        .withEndTime(Instant.ofEpochMilli(batchNotification.getTimestamp()))
        .withTags(new HashMap<>());
    traceComponent.getTags().put(MULE_BATCH_JOB_INSTANCE_ID.getKey(), jobInstance.getId());
    traceComponent.getTags().put(MULE_BATCH_JOB_NAME.getKey(), jobInstance.getOwnerJobName());
    traceComponent.getTags().put(MULE_BATCH_JOB_STEP_NAME.getKey(), batchStep.getName());

    openTelemetryConnection.endProcessorSpan(traceComponent, null);
  }
}
