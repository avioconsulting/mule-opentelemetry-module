package com.avioconsulting.mule.opentelemetry.spans;

import com.avioconsulting.mule.opentelemetry.OpenTelemetryStarter;
import com.avioconsulting.mule.opentelemetry.api.processors.*;
import com.avioconsulting.mule.opentelemetry.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.store.TransactionStore;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class OpenTelemetryMuleEventProcessor {

    //TODO: we are processing async notifications. Actual event time could be different.
    // Using notification.getTimestamp() to set the span times. Validate if this creates any TZ issues in distributed.

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

    private static final TransactionStore transactionStore = InMemoryTransactionStore.getInstance();

    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling '{}' processor start event", notification.getComponent().getIdentifier());
        try {
            ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
                    .getProcessorComponentFor(notification.getComponent().getIdentifier())
                    .orElse(new GenericProcessorComponent());
            TraceComponent traceComponent = processorComponent.getStartTraceComponent(notification);
            SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance().getTracer().spanBuilder(traceComponent.getSpanName())
                    .setSpanKind(traceComponent.getSpanKind()).setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
            traceComponent.getTags().forEach(spanBuilder::setAttribute);
            transactionStore.addProcessorSpan(traceComponent.getTransactionId(), traceComponent.getLocation(), spanBuilder);
        } catch (Exception ex) {
            logger.error("Error in handling processor start event", ex);
            throw ex;
        }
    }

    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        try {
            logger.debug("Handling '{}' processor end event ", notification.getComponent().getIdentifier());
            ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
                    .getProcessorComponentFor(notification.getComponent().getIdentifier())
                    .orElse(new GenericProcessorComponent());
            TraceComponent traceComponent = processorComponent.getEndTraceComponent(notification);
            transactionStore.endProcessorSpan(traceComponent.getTransactionId(), traceComponent.getLocation(), span -> {
                if(notification.getEvent().getError().isPresent()) {
                    Error error = notification.getEvent().getError().get();
                    span.setStatus(StatusCode.ERROR, error.getDescription());
                    span.recordException(error.getCause());
                }
                if(traceComponent.getTags() != null)traceComponent.getTags().forEach(span::setAttribute);
            });
        } catch (Exception ex) {
            logger.error("Error in handling processor end event", ex);
            throw ex;
        }
    }

	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
        try {
            logger.debug("Handling '{}' flow start event", notification.getComponent().getIdentifier());
            FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
            TraceComponent traceComponent = flowProcessorComponent.getStartTraceComponent(notification);
            SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance().getTracer()
                    .spanBuilder(traceComponent.getSpanName())
                    .setSpanKind(SpanKind.SERVER)
                    .setParent(traceComponent.getContext())
                    .setStartTimestamp(Instant.ofEpochMilli(notification.getTimestamp()));
            traceComponent.getTags().forEach(spanBuilder::setAttribute);
            transactionStore.startTransaction(traceComponent.getTransactionId(), traceComponent.getName(), spanBuilder);
        } catch (Exception ex) {
            logger.error("Error in handling flow start event", ex);
            throw ex;
        }
	}

	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
        try {
            logger.debug("Handling '{}' flow end event", notification.getComponent().getIdentifier());
            FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
            TraceComponent traceComponent = flowProcessorComponent.getEndTraceComponent(notification);
            transactionStore.endTransaction(traceComponent.getTransactionId(), traceComponent.getName(), rootSpan -> {
                if(notification.getException() != null) {
                    rootSpan.setStatus(StatusCode.ERROR, notification.getException().getMessage());
                    rootSpan.recordException(notification.getException());
                }
            });
        } catch (Exception ex) {
            logger.error("Error in handling flow end event", ex);
            throw ex;
        }
    }

}