package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Notification Processor bean. This is injected through registry-bootstrap into
 * Extension configuration,
 * see
 * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
 */
public class MuleNotificationProcessor {

  private static final Logger logger = LoggerFactory.getLogger(MuleNotificationProcessor.class);
  public static final String MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME = "mule.otel.span.processors.enable";

  private Supplier<OpenTelemetryConnection> connectionSupplier;
  private boolean spanAllProcessors;
  private TraceLevelConfiguration traceLevelConfiguration;
  private OpenTelemetryConnection openTelemetryConnection;

  ConfigurationComponentLocator configurationComponentLocator;

  private ProcessorComponentService processorComponentService;
  private final ProcessorComponent flowProcessorComponent;

  /**
   * Collect all otel specific system properties and cache them in a map.
   */
  private final Map<String, String> systemPropMap = System.getProperties().stringPropertyNames().stream()
      .filter(p -> p.contains(".otel.")).collect(Collectors.toMap(String::toLowerCase, System::getProperty));

  /**
   * This {@link GenericProcessorComponent} will be used for processors that do
   * not have a specific processor like {@link HttpProcessorComponent}.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // Avoid to creation object per notification instance
  private final Optional<ProcessorComponent> genericProcessorComponent;

  @Inject
  public MuleNotificationProcessor(ConfigurationComponentLocator configurationComponentLocator) {
    this.configurationComponentLocator = configurationComponentLocator;
    flowProcessorComponent = new FlowProcessorComponent()
        .withConfigurationComponentLocator(configurationComponentLocator);
    genericProcessorComponent = Optional
        .of(new GenericProcessorComponent().withConfigurationComponentLocator(configurationComponentLocator));
  }

  public boolean hasConnection() {
    return connectionSupplier != null;
  }

  public Supplier<OpenTelemetryConnection> getConnectionSupplier() {
    return connectionSupplier;
  }

  public void init(Supplier<OpenTelemetryConnection> connectionSupplier, boolean spanAllProcessors) {
    init(connectionSupplier, new TraceLevelConfiguration(spanAllProcessors, Collections.emptyList()));
  }

  public void init(Supplier<OpenTelemetryConnection> connectionSupplier,
      TraceLevelConfiguration traceLevelConfiguration) {
    this.connectionSupplier = connectionSupplier;
    this.spanAllProcessors = Boolean.parseBoolean(System.getProperty(MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME,
        Boolean.toString(traceLevelConfiguration.isSpanAllProcessors())));
    this.traceLevelConfiguration = traceLevelConfiguration;
    processorComponentService = ProcessorComponentService.getInstance();
  }

  private void init() {
    if (openTelemetryConnection == null) {
      openTelemetryConnection = connectionSupplier.get();
    }
  }

  /**
   * <pre>
   * Extract any attributes defined via system properties (see {@link System#getProperties()}) for provided <code>configName</code>.
   *
   * It uses `{configName}.otel.{attributeKey}` pattern to identify relevant system properties. Key matching is case-insensitive.
   * </pre>
   *
   * @param configName
   *            {@link String} name of the component's global configuration
   *            element
   * @param tags
   *            Modifiable {@link Map} to populate any
   */
  protected final void globalConfigSystemAttributes(String configName, Map<String, String> tags) {
    if (configName == null || configName.trim().isEmpty())
      return;
    Objects.requireNonNull(tags, "Tags map cannot be null");
    String configRef = configName.toLowerCase();
    String replaceVal = configRef + ".otel.";
    systemPropMap.entrySet().stream().filter(e -> e.getKey().startsWith(configRef)).forEach(entry -> {
      String propKey = entry.getKey().substring(replaceVal.length());
      tags.put(propKey, entry.getValue());
    });
  }

  public void handleProcessorStartEvent(MessageProcessorNotification notification) {
    try {
      getProcessorComponent(notification)
          .ifPresent(processor -> {
            logger.trace(
                "Handling '{}:{}' processor start event",
                notification.getResourceIdentifier(),
                notification.getComponent().getIdentifier());
            init();
            TraceComponent traceComponent = processor.getStartTraceComponent(notification);
            SpanBuilder spanBuilder = openTelemetryConnection
                .spanBuilder(traceComponent.getSpanName())
                .setSpanKind(traceComponent.getSpanKind())
                .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
            globalConfigSystemAttributes(
                traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey()),
                traceComponent.getTags());
            traceComponent.getTags().forEach(spanBuilder::setAttribute);
            openTelemetryConnection.getTransactionStore().addProcessorSpan(
                traceComponent.getTransactionId(),
                notification.getComponent().getLocation().getRootContainerName(),
                traceComponent.getLocation(), spanBuilder);
          });

    } catch (Exception ex) {
      logger.error("Error in handling processor start event", ex);
      throw ex;
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
  Optional<ProcessorComponent> getProcessorComponent(MessageProcessorNotification notification) {
    ComponentIdentifier identifier = notification.getComponent().getIdentifier();
    return getProcessorComponent(identifier);
  }

  public Optional<ProcessorComponent> getProcessorComponent(ComponentIdentifier identifier) {
    boolean ignored = traceLevelConfiguration.getIgnoreMuleComponents().stream()
        .anyMatch(mc -> mc.getNamespace().equalsIgnoreCase(identifier.getNamespace())
            & (mc.getName().equalsIgnoreCase(identifier.getName()) || "*".equalsIgnoreCase(mc.getName())));
    if (spanAllProcessors && ignored)
      return Optional.empty();

    Optional<ProcessorComponent> processorComponent = processorComponentService
        .getProcessorComponentFor(identifier, configurationComponentLocator);

    if (!processorComponent.isPresent() && spanAllProcessors) {
      processorComponent = genericProcessorComponent;
    }
    return processorComponent;
  }

  public void handleProcessorEndEvent(MessageProcessorNotification notification) {
    try {
      getProcessorComponent(notification)
          .ifPresent(processorComponent -> {
            logger.trace(
                "Handling '{}:{}' processor end event ",
                notification.getResourceIdentifier(),
                notification.getComponent().getIdentifier());
            init();
            TraceComponent traceComponent = processorComponent.getEndTraceComponent(notification);
            openTelemetryConnection.getTransactionStore().endProcessorSpan(
                traceComponent.getTransactionId(),
                traceComponent.getLocation(),
                span -> {

                  if (notification.getEvent().getError().isPresent()) {
                    Error error = notification.getEvent().getError().get();
                    span.recordException(error.getCause());
                  }
                  setSpanStatus(traceComponent, span);
                  if (traceComponent.getTags() != null)
                    traceComponent.getTags().forEach(span::setAttribute);
                },
                Instant.ofEpochMilli(notification.getTimestamp()));
          });
    } catch (Exception ex) {
      logger.error("Error in handling processor end event", ex);
      throw ex;
    }
  }

  public void handleFlowStartEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow start event", notification.getResourceIdentifier());
      init();
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceStartTraceComponent(notification, openTelemetryConnection).get();
      SpanBuilder spanBuilder = openTelemetryConnection
          .spanBuilder(traceComponent.getSpanName())
          .setSpanKind(traceComponent.getSpanKind())
          .setParent(traceComponent.getContext())
          .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));

      globalConfigSystemAttributes(
          traceComponent.getTags().get(SemanticAttributes.MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey()),
          traceComponent.getTags());

      traceComponent.getTags().forEach(spanBuilder::setAttribute);
      openTelemetryConnection.getTransactionStore().startTransaction(
          traceComponent.getTransactionId(), traceComponent.getName(), spanBuilder);
    } catch (Exception ex) {
      logger.error(
          "Error in handling "
              + notification.getResourceIdentifier()
              + " flow start event",
          ex);
      throw ex;
    }
  }

  public void handleFlowEndEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow end event", notification.getResourceIdentifier());
      init();
      flowProcessorComponent
          .getSourceEndTraceComponent(notification, openTelemetryConnection)
          .ifPresent(traceComponent -> {
            openTelemetryConnection.getTransactionStore().endTransaction(
                traceComponent.getTransactionId(),
                traceComponent.getName(),
                rootSpan -> {
                  traceComponent.getTags().forEach(rootSpan::setAttribute);
                  setSpanStatus(traceComponent, rootSpan);
                  if (notification.getException() != null) {
                    rootSpan.recordException(notification.getException());
                  }
                },
                Instant.ofEpochMilli(notification.getTimestamp()));
          });
    } catch (Exception ex) {
      logger.error(
          "Error in handling " + notification.getResourceIdentifier() + " flow end event",
          ex);
      throw ex;
    }
  }

  private void setSpanStatus(TraceComponent traceComponent, Span span) {
    if (traceComponent.getStatusCode() != null
        && !StatusCode.UNSET.equals(traceComponent.getStatusCode())) {
      span.setStatus(traceComponent.getStatusCode());
    }
  }
}
