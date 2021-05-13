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
		
        Span my_test_span = SpanRegistrationUtility.createSpan("my_test_span").startSpan();
        my_test_span.addEvent("Flow Start");
        transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), my_test_span);
  	
	}
	// What to invoke when Mule process step ends.
	public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
		logger.debug("Handling end event");
	
		Span span = transactionStore.retrieveSpan(getTransactionId(notification), getSpanId(notification));

	
		span.end();
	}


	public static String getSpanId(MessageProcessorNotification notification) {
		return notification.getInfo().getComponent().getLocation().getLocation();
	}


	
	private static String getTransactionId(MessageProcessorNotification notification) {
		return notification.getEvent().getCorrelationId();
	}

}