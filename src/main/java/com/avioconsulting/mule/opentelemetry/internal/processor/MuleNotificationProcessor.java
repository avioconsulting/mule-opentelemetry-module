package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import io.opentelemetry.api.trace.SpanKind;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_SCOPE_SUBFLOW_NAME;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.findLocation;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.getTraceComponent;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.isFlowRef;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.resolveFlowName;

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
  private final List<String> interceptSpannedComponents = new ArrayList<>();
  private final List<String> meteredComponentLocations = new ArrayList<>();
  private ProcessorComponentService processorComponentService;
  private final ProcessorComponent flowProcessorComponent;

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
  }

  /**
   * Locations that are intercepted for span creation. These will be excluded from
   * span creation from notifications.
   * 
   * @param location
   *            {@link String} value of target processor
   */
  public void addInterceptSpannedComponents(String location) {
    interceptSpannedComponents.add(location);
  }

  /**
   * Locations that are intercepted and eligible for capturing metrics.
   * 
   * @param location
   *            {@link String} value of target processor
   */
  public void addMeteredComponentLocation(String location) {
    if (openTelemetryConnection != null && !openTelemetryConnection.isTurnOffMetrics()) {
      openTelemetryConnection.getMetricsProviders().addMeteredComponent(location);
    }
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

  public TraceLevelConfiguration getTraceLevelConfiguration() {
    return traceLevelConfiguration;
  }

  public void init(OpenTelemetryConnection connection,
      TraceLevelConfiguration traceLevelConfiguration) {
    this.openTelemetryConnection = connection;
    this.spanAllProcessors = Boolean.parseBoolean(System.getProperty(MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME,
        Boolean.toString(traceLevelConfiguration.isSpanAllProcessors())));
    this.traceLevelConfiguration = traceLevelConfiguration;
    processorComponentService = ProcessorComponentService.getInstance();
  }

  public void handleProcessorStartEvent(MessageProcessorNotification notification) {
    String location = notification.getComponent().getLocation().getLocation();
    if (ComponentsUtil.isAsyncScope(notification.getComponent().getLocation().getComponentIdentifier())) {
      // Async scopes are handled via AsyncMessageNotifications.
      // Creating one here will create duplicate spans
      return;
    }
    if (interceptSpannedComponents.contains(location)) {
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
        openTelemetryConnection.addProcessorSpan(traceComponent,
            ComponentsUtil.getLocationParent(notification.getComponent().getLocation().getLocation()));
        processFlowRef(traceComponent, notification.getEvent());
      }
    } catch (Exception ex) {
      logger.error("Error in handling processor start event", ex);
      throw ex;
    }
  }

  private void processFlowRef(TraceComponent traceComponent, Event event) {
    if (isFlowRef(traceComponent.getComponentLocation())) {
      Optional<ComponentLocation> subFlowLocation = resolveFlowName(
          getOpenTelemetryConnection().getExpressionManager(), traceComponent, event.asBindingContext(),
          configurationComponentLocator);
      if (subFlowLocation.isPresent()) {
        TraceComponent subflowTrace = getTraceComponent(subFlowLocation.get(), traceComponent);
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
    boolean ignored = traceLevelConfiguration.getIgnoreMuleComponents().stream()
        .anyMatch(mc -> mc.getNamespace().equalsIgnoreCase(identifier.getNamespace())
            & (mc.getName().equalsIgnoreCase(identifier.getName()) || "*".equalsIgnoreCase(mc.getName())));
    if (spanAllProcessors && ignored)
      return null;

    ProcessorComponent processorComponent = processorComponentService
        .getProcessorComponentFor(identifier, configurationComponentLocator);

    if (processorComponent == null && spanAllProcessors) {
      processorComponent = genericProcessorComponent;
    }
    return processorComponent;
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
                    TraceComponent subflowTrace = TraceComponent.of(subFlowComp)
                        .withTransactionId(traceComponent.getTransactionId())
                        .withSpanName(subFlowComp.getLocation())
                        .withSpanKind(SpanKind.INTERNAL)
                        .withTags(Collections.singletonMap(MULE_APP_SCOPE_SUBFLOW_NAME.getKey(),
                            subFlowComp.getLocation()))
                        .withStatsCode(traceComponent.getStatusCode())
                        .withEndTime(traceComponent.getEndTime())
                        .withContext(traceComponent.getContext())
                        .withEventContextId(notification.getEvent().getContext().getId());
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
      openTelemetryConnection.startTransaction(traceComponent);
    } catch (Exception ex) {
      logger.error(
          "Error in handling "
              + notification.getResourceIdentifier()
              + " flow start event",
          ex);
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
      logger.info("Getting context for {} with {}", notification.getResourceIdentifier(), expression);
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
        return openTelemetryConnection.getTraceContext(((Map) contextCarrier.getValue()),
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
      TransactionMeta transactionMeta = openTelemetryConnection.endTransaction(traceComponent,
          notification.getException());
      if (transactionMeta == null) {
        // If transaction isn't found by the current context,
        // search by any context from variable
        TypedValue<String> contextId = (TypedValue<String>) notification.getEvent().getVariables()
            .get(TransactionStore.OTEL_FLOW_CONTEXT_ID);
        if (contextId != null && contextId.getValue() != null) {
          traceComponent = traceComponent.withEventContextId(contextId.getValue());
        }
        transactionMeta = openTelemetryConnection.endTransaction(traceComponent,
            notification.getException());
      }

      openTelemetryConnection.getMetricsProviders().captureFlowMetrics(
          Objects.requireNonNull(transactionMeta,
              "Transaction for " + traceComponent.contextScopedLocation() + " cannot be null"),
          notification.getResourceIdentifier(),
          notification.getException());

    } catch (Exception ex) {
      logger.error(
          "Error in handling " + notification.getResourceIdentifier() + " flow end event",
          ex);
      throw ex;
    }
  }
}
