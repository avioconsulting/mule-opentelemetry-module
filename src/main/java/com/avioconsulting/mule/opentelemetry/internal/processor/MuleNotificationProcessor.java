package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
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
import java.util.Optional;
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

  @Inject
  ConfigurationComponentLocator configurationComponentLocator;

  private final FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();

  private ProcessorComponentService processorComponentService;
  private final GenericProcessorComponent genericProcessorComponent = new GenericProcessorComponent();

  public MuleNotificationProcessor() {

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
            traceComponent.getTags().forEach(spanBuilder::setAttribute);
            openTelemetryConnection.getTransactionStore().addProcessorSpan(
                traceComponent.getTransactionId(), traceComponent.getLocation(), spanBuilder);
          });

    } catch (Exception ex) {
      logger.error("Error in handling processor start event", ex);
      throw ex;
    }
  }

  Optional<ProcessorComponent> getProcessorComponent(MessageProcessorNotification notification) {
    ComponentIdentifier identifier = notification.getComponent().getIdentifier();
    boolean ignored = traceLevelConfiguration.getIgnoreMuleComponents().stream()
        .anyMatch(mc -> mc.getNamespace().equalsIgnoreCase(identifier.getNamespace())
            & (mc.getName().equalsIgnoreCase(identifier.getName()) || "*".equalsIgnoreCase(mc.getName())));
    if (spanAllProcessors && ignored)
      return Optional.empty();

    Optional<ProcessorComponent> processorComponent = processorComponentService
        .getProcessorComponentFor(identifier, configurationComponentLocator);

    if (!processorComponent.isPresent() && spanAllProcessors) {
      processorComponent = Optional
          .of(genericProcessorComponent.withConfigurationComponentLocator(configurationComponentLocator));
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
      flowProcessorComponent
          .withConfigurationComponentLocator(configurationComponentLocator);
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceStartTraceComponent(notification, openTelemetryConnection).get();
      SpanBuilder spanBuilder = openTelemetryConnection
          .spanBuilder(traceComponent.getSpanName())
          .setSpanKind(traceComponent.getSpanKind())
          .setParent(traceComponent.getContext())
          .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
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
          .withConfigurationComponentLocator(configurationComponentLocator);
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceEndTraceComponent(notification, openTelemetryConnection).get();
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
