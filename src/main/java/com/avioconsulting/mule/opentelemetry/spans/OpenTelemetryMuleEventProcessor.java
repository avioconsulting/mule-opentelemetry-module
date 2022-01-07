package com.avioconsulting.mule.opentelemetry.spans;

import com.avioconsulting.mule.opentelemetry.OpenTelemetryStarter;
import com.avioconsulting.mule.opentelemetry.api.processors.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OpenTelemetryMuleEventProcessor {

    private static Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

    private static TransactionStore transactionStore = new TransactionStore();

    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor start event");
        ProcessorComponent processorComponent = ProcessorComponentService.getInstance().getProcessorComponentFor(notification.getComponent().getIdentifier()).orElse(new GenericProcessorComponent());
        TraceComponent traceComponent = processorComponent.getTraceComponent(notification);
        SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance().getTracer().spanBuilder(traceComponent.getName());
        traceComponent.getTags().forEach(spanBuilder::setAttribute);
        transactionStore.addSpan(traceComponent.getTransactionId(),traceComponent.getSpanId(),  spanBuilder);
    }

    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor end event");
        ProcessorComponent processorComponent = ProcessorComponentService.getInstance().getProcessorComponentFor(notification.getComponent().getIdentifier()).orElse(new GenericProcessorComponent());
        TraceComponent traceComponent = processorComponent.getTraceComponent(notification);
        Span span = transactionStore.getSpan(traceComponent.getTransactionId(), traceComponent.getSpanId());
        span.end();
    }

	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");
        FlowProcessorComponent flowProcessorComponent = new FlowProcessorComponent();
        TraceComponent traceComponent = flowProcessorComponent.getTraceComponent(notification);
        SpanBuilder spanBuilder = OpenTelemetryStarter.getInstance().getTracer().spanBuilder(traceComponent.getSpanId()).setSpanKind(SpanKind.SERVER);
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