package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionMeta;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.notification.ExtensionNotification;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

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
  private final List<String> interceptSpannedComponents = new ArrayList<>();
  private final List<String> meteredComponentLocations = new ArrayList<>();
  private ProcessorComponentService processorComponentService;
  private final ProcessorComponent flowProcessorComponent;

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
    meteredComponentLocations.add(location);
  }

  public boolean hasConnection() {
    return connectionSupplier != null;
  }

  public Supplier<OpenTelemetryConnection> getConnectionSupplier() {
    return connectionSupplier;
  }

  public TraceLevelConfiguration getTraceLevelConfiguration() {
    return traceLevelConfiguration;
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

  public void handleProcessorStartEvent(MessageProcessorNotification notification) {
    String location = notification.getComponent().getLocation().getLocation();
    if (interceptSpannedComponents.contains(location)) {
      logger.trace(
          "Component {} will be processed by interceptor, skipping notification processing to create span",
          location);
      return;
    }
    try {
      ProcessorComponent processorComponent = getProcessorComponent(notification);
      if (processorComponent != null) {
        logger.trace(
            "Handling '{}:{}' processor start event",
            notification.getResourceIdentifier(),
            notification.getComponent().getIdentifier());
        init();
        TraceComponent traceComponent = processorComponent.getStartTraceComponent(notification)
            .withStartTime(Instant.ofEpochMilli(notification.getTimestamp()));
        openTelemetryConnection.addProcessorSpan(traceComponent,
            notification.getComponent().getLocation().getRootContainerName());
      }
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

  public void handleProcessorEndEvent(MessageProcessorNotification notification) {
    String location = notification.getComponent().getLocation().getLocation();
    try {
      ProcessorComponent processorComponent = getProcessorComponent(notification);
      if (processorComponent != null) {
        logger.trace(
            "Handling '{}:{}' processor end event ",
            notification.getResourceIdentifier(),
            notification.getComponent().getIdentifier());
        init();
        TraceComponent traceComponent = processorComponent.getEndTraceComponent(notification)
            .withEndTime(Instant.ofEpochMilli(notification.getTimestamp()));
        openTelemetryConnection.endProcessorSpan(traceComponent,
            notification.getEvent().getError().orElse(null));
      }
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
          .getSourceStartTraceComponent(notification, openTelemetryConnection)
          .withStartTime(Instant.ofEpochMilli(notification.getTimestamp()));
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

  public void handleFlowEndEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow end event", notification.getResourceIdentifier());
      init();
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceEndTraceComponent(notification, openTelemetryConnection)
          .withEndTime(Instant.ofEpochMilli(notification.getTimestamp()));
      openTelemetryConnection.endTransaction(traceComponent, notification.getException());
    } catch (Exception ex) {
      logger.error(
          "Error in handling " + notification.getResourceIdentifier() + " flow end event",
          ex);
      throw ex;
    }
  }

}
