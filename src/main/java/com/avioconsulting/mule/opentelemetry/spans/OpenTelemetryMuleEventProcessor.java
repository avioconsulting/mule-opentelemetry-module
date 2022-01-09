package com.avioconsulting.mule.opentelemetry.spans;

import com.avioconsulting.mule.opentelemetry.OpenTelemetryStarter;
import com.avioconsulting.mule.opentelemetry.api.processors.*;
import com.avioconsulting.mule.opentelemetry.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.store.TransactionStore;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OpenTelemetryMuleEventProcessor {

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
                    .setSpanKind(traceComponent.getSpanKind());
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
            transactionStore.endProcessorSpan(traceComponent.getTransactionId(), traceComponent.getLocation());
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
                    .setParent(traceComponent.getContext());
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
            transactionStore.endTransaction(traceComponent.getTransactionId(), traceComponent.getName());
        } catch (Exception ex) {
            logger.error("Error in handling flow end event", ex);
            throw ex;
        }
    }

}