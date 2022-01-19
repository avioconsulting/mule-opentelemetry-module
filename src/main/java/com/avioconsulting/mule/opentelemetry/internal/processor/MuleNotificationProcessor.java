package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

public class MuleNotificationProcessor {

  private static final Logger logger = LoggerFactory.getLogger(MuleNotificationProcessor.class);
  public static final String MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME = "mule.otel.span.processors.enable";

  private final Supplier<OpenTelemetryConnection> connectionSupplier;
  private final boolean spanAllProcessors;
  private OpenTelemetryConnection openTelemetryConnection;

  public MuleNotificationProcessor(Supplier<OpenTelemetryConnection> connectionSupplier, boolean spanAllProcessors) {
    this.connectionSupplier = connectionSupplier;
    this.spanAllProcessors = Boolean.parseBoolean(System.getProperty(MULE_OTEL_SPAN_PROCESSORS_ENABLE_PROPERTY_NAME,
        Boolean.toString(spanAllProcessors)));
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

  private Optional<ProcessorComponent> getProcessorComponent(MessageProcessorNotification notification) {
    Optional<ProcessorComponent> processorComponent = ProcessorComponentService.getInstance()
        .getProcessorComponentFor(notification.getComponent().getIdentifier());

    if (!processorComponent.isPresent() && spanAllProcessors) {
      processorComponent = Optional.of(new GenericProcessorComponent());
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
                    span.setStatus(StatusCode.ERROR, error.getDescription());
                    span.recordException(error.getCause());
                  }
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
      ProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
      TraceComponent traceComponent = flowProcessorComponent
          .getSourceTraceComponent(notification, openTelemetryConnection).get();
      SpanBuilder spanBuilder = openTelemetryConnection
          .spanBuilder(traceComponent.getSpanName())
          .setSpanKind(SpanKind.SERVER)
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
      FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
      TraceComponent traceComponent = flowProcessorComponent.getEndTraceComponent(notification);
      openTelemetryConnection.getTransactionStore().endTransaction(
          traceComponent.getTransactionId(),
          traceComponent.getName(),
          rootSpan -> {
            if (notification.getException() != null) {
              rootSpan.setStatus(
                  StatusCode.ERROR, notification.getException().getMessage());
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
}
