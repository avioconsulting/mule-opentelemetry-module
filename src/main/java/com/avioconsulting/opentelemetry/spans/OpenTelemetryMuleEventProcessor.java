package com.avioconsulting.opentelemetry.spans;

import com.avioconsulting.opentelemetry.OpenTelemetryStarter;
import io.opentelemetry.api.trace.Span;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;


public class OpenTelemetryMuleEventProcessor {


    private static Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

    // Store for all active transactions in flight.
    private static TransactionStore transactionStore = new TransactionStore();

    // What to invoke when Mule process step starts.
    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor start event");
//        my_test_span.setAttribute("mule_param","some_value");
//        my_test_span.addEvent("Processor Start");
        transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)));

    }

    // What to invoke when Mule process step ends.
    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        logger.debug("Handling end event");

        Span span = transactionStore.getSpan(getTransactionId(notification), getSpanId(notification));
        span.end();
    }


	// What to invoke when Mule flow starts execution.
	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");

		if (!transactionStore.isTransactionPresent(getTransactionId(notification))) {
            transactionStore.storeTransaction(getTransactionId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)).startSpan());
        } else {
		    transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)));
        }
	}

	// What to invoke when Mule flow completes execution.
	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow end event");

        Optional<Map<String, Span>> stringSpanMap = transactionStore.retrieveTransaction(getTransactionId(notification));
        stringSpanMap.ifPresent(spanMap -> spanMap.forEach(
                (s, span) -> span.end()
        ));
    }

    public static String getSpanName(MessageProcessorNotification notification) {
		String name = notification.getComponent().getIdentifier().getName();
		return name;
    }

    public static String getSpanName(PipelineMessageNotification notification) {
        String name = notification.getComponent().getIdentifier().getName();
        return name;
    }

    public static String getSpanId(MessageProcessorNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }

    public static String getSpanId(PipelineMessageNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }


    private static String getTransactionId(MessageProcessorNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

    private static String getTransactionId(PipelineMessageNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

}