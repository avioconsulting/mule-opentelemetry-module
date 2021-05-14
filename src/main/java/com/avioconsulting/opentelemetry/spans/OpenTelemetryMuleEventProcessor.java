package com.avioconsulting.opentelemetry.spans;

import com.avioconsulting.opentelemetry.OpenTelemetryStarter;
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

    // What to invoke when Mule process step starts.
    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling start event");
        Span span = null;
        try {
            SpanBuilder spanBuilder = OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)).setNoParent();
            span = spanBuilder.startSpan();
        } catch (Exception e) {
            System.out.println(e);
        }
//        my_test_span.setAttribute("mule_param","some_value");
//        my_test_span.addEvent("Processor Start");
        transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), span);

    }

    // What to invoke when Mule process step ends.
    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        logger.debug("Handling end event");

        Span span = transactionStore.retrieveSpan(getTransactionId(notification), getSpanId(notification));
        span.end();
    }


	// What to invoke when Mule flow starts execution.
	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");

		if (transactionStore.isTransactionPresent(getTransactionId(notification)))
            transactionStore.storeTransaction(getTransactionId(notification), notification);

	}

	// What to invoke when Mule flow completes execution.
	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow end event");

		TransactionUtils.endTransaction(transactionStore, notification);
	}

    public static String getSpanName(MessageProcessorNotification notification) {
		String name = notification.getComponent().getIdentifier().getName();
		return name;
    }

    public static String getSpanId(MessageProcessorNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }


    private static String getTransactionId(MessageProcessorNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

    private static String getTransactionId(PipelineMessageNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

}