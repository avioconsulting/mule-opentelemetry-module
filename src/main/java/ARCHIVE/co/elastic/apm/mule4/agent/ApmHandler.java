package ARCHIVE.co.elastic.apm.mule4.agent;

import org.mule.runtime.api.notification.ExceptionNotification;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ARCHIVE.co.elastic.apm.mule4.agent.exception.ExceptionUtils;
import ARCHIVE.co.elastic.apm.mule4.agent.span.SpanUtils;
import ARCHIVE.co.elastic.apm.mule4.agent.transaction.TransactionStore;
import ARCHIVE.co.elastic.apm.mule4.agent.transaction.TransactionUtils;

/*
 * Adapter called by flow event handlers and flow process step interceptors
 */
public class ApmHandler {

	private static Logger logger = LoggerFactory.getLogger(ApmHandler.class);

	// Store for all active transactions in flight.
	private static TransactionStore transactionStore = new TransactionStore();

	// What to invoke when Mule process step starts.
	public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
		logger.debug("Handling start event");
		
		SpanUtils.startSpan(transactionStore, notification);
	}

	// What to invoke when Mule process step ends.
	public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
		logger.debug("Handling end event");

		SpanUtils.endSpan(transactionStore, notification);
	}

	// What to invoke when an Exception thrown in the Mule flow.
	public static void handleExceptionEvent(ExceptionNotification notification) {
		logger.debug("Handling exception event");

		ExceptionUtils.captureException(transactionStore, notification);
	}

	// What to invoke when Mule flow starts execution.
	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");

		if (TransactionUtils.isFirstEvent(transactionStore, notification))
			TransactionUtils.startTransaction(transactionStore, notification);

	}

	// What to invoke when Mule flow completes execution.
	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow end event");

		TransactionUtils.endTransaction(transactionStore, notification);
	}

}
