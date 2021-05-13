package com.avioconsulting.opentelemetry.spans;

import io.opentelemetry.api.trace.Span;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryMuleEventProcessor {


	private static Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

	// Store for all active transactions in flight.
	private static TransactionStore transactionStore = new TransactionStore();

	// What to invoke when Mule process step starts.
	public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
		logger.debug("Handling start event");
		
        Span span = SpanRegistrationUtility.createSpan(getSpanName(notification)).startSpan();
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

    public static String getSpanName(MessageProcessorNotification notification) {
    	return notification.getComponent().getIdentifier().getName();
    }
	public static String getSpanId(MessageProcessorNotification notification) {
		return notification.getInfo().getComponent().getLocation().getLocation();
	}


	
	private static String getTransactionId(MessageProcessorNotification notification) {
		return notification.getEvent().getCorrelationId();
	}

}