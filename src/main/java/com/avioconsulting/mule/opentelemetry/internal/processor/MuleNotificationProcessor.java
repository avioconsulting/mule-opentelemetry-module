package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.OpenTelemetryStarter;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import java.time.Instant;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuleNotificationProcessor {

  // TODO: we are processing async notifications. Actual event time could be
  // different.
  // Using notification.getTimestamp() to set the span times. Validate if this
  // creates any TZ
  // issues in distributed.
  private static final Logger logger = LoggerFactory.getLogger(MuleNotificationProcessor.class);

  private static final TransactionStore transactionStore = InMemoryTransactionStore.getInstance();

  public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
    logger.trace(
        "Handling '{}:{}' processor start event",
        notification.getResourceIdentifier(),
        notification.getComponent().getIdentifier());
    try {
      ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
          .getProcessorComponentFor(notification.getComponent().getIdentifier())
          .orElse(new GenericProcessorComponent());
      TraceComponent traceComponent = processorComponent.getStartTraceComponent(notification);
      SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance()
          .getTracer()
          .spanBuilder(traceComponent.getSpanName())
          .setSpanKind(traceComponent.getSpanKind())
          .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
      traceComponent.getTags().forEach(spanBuilder::setAttribute);
      transactionStore.addProcessorSpan(
          traceComponent.getTransactionId(), traceComponent.getLocation(), spanBuilder);
    } catch (Exception ex) {
      logger.error("Error in handling processor start event", ex);
      throw ex;
    }
  }

  public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
    try {
      logger.trace(
          "Handling '{}:{}' processor end event ",
          notification.getResourceIdentifier(),
          notification.getComponent().getIdentifier());
      ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
          .getProcessorComponentFor(notification.getComponent().getIdentifier())
          .orElse(new GenericProcessorComponent());
      TraceComponent traceComponent = processorComponent.getEndTraceComponent(notification);
      transactionStore.endProcessorSpan(
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
    } catch (Exception ex) {
      logger.error("Error in handling processor end event", ex);
      throw ex;
    }
  }

  public static void handleFlowStartEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow start event", notification.getResourceIdentifier());
      FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
      TraceComponent traceComponent = flowProcessorComponent.getStartTraceComponent(notification);
      SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance()
          .getTracer()
          .spanBuilder(traceComponent.getSpanName())
          .setSpanKind(SpanKind.SERVER)
          .setParent(traceComponent.getContext())
          .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
      traceComponent.getTags().forEach(spanBuilder::setAttribute);
      transactionStore.startTransaction(
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

  public static void handleFlowEndEvent(PipelineMessageNotification notification) {
    try {
      logger.trace("Handling '{}' flow end event", notification.getResourceIdentifier());
      FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
      TraceComponent traceComponent = flowProcessorComponent.getEndTraceComponent(notification);
      transactionStore.endTransaction(
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
