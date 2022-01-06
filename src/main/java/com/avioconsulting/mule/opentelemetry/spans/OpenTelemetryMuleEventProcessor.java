package com.avioconsulting.mule.opentelemetry.spans;

import com.avioconsulting.mule.opentelemetry.OpenTelemetryStarter;
import com.avioconsulting.mule.opentelemetry.api.processors.FlowProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.processors.GenericProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.processors.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OpenTelemetryMuleEventProcessor {

    private static Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

    // Store for all active transactions in flight.
    private static TransactionStore transactionStore = new TransactionStore();

    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor start event");
        TraceComponent traceComponent = new GenericProcessorComponent().getTraceComponent(notification);
        SpanBuilder spanBuilder = OpenTelemetryStarter.getTracer().spanBuilder(traceComponent.getName());
        traceComponent.getTags().forEach(spanBuilder::setAttribute);
        transactionStore.addSpan(traceComponent.getTransactionId(),traceComponent.getSpanId(),  spanBuilder);
    }

    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor end event");
        TraceComponent traceComponent = new GenericProcessorComponent().getTraceComponent(notification);
        Span span = transactionStore.getSpan(traceComponent.getTransactionId(), traceComponent.getSpanId());
        span.end();
    }

	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");
        FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
        TraceComponent traceComponent = flowProcessorComponent.getTraceComponent(notification);
        SpanBuilder spanBuilder = OpenTelemetryStarter.getTracer().spanBuilder(traceComponent.getName());
        traceComponent.getTags().forEach(spanBuilder::setAttribute);
        transactionStore.storeTransaction(traceComponent.getTransactionId(), spanBuilder.startSpan());
	}

	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow end event");
        FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
        TraceComponent traceComponent = flowProcessorComponent.getTraceComponent(notification);
		transactionStore.endTransaction(traceComponent.getTransactionId());
    }

}