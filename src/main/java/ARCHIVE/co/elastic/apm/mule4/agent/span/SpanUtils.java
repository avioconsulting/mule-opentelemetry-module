package ARCHIVE.co.elastic.apm.mule4.agent.span;

import javax.xml.namespace.QName;

import ARCHIVE.co.elastic.apm.mule4.agent.tracing.HttpTracingUtils;
import ARCHIVE.co.elastic.apm.mule4.agent.transaction.TransactionStore;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

/*
 * Creation and ending of APM Spans.
 */
public class SpanUtils {
	private static final String SUBTYPE = "mule-step";
	private static final String DOC_NAME = "name";
	private static final String DOC_NAMESPACE = "http://www.mulesoft.org/schema/mule/documentation";
	private static final String UNNAMED = "...";
	private static final String UNTYPED = "zzz_type";

	private static final Logger logger = LoggerFactory.getLogger(SpanUtils.class);

	/*
	 * Start a span
	 */
	public static void startSpan(TransactionStore transactionStore, MessageProcessorNotification notification) {
		String transactionId = getTransactionId(notification);

		// Span can only be started if there is an existing transaction created by flow
		// listener.
		Transaction transaction = transactionStore.getTransaction(transactionId)
				.orElse(getCurrentTransactionAndLog(transactionId));

		Span span = transaction.startSpan(getSpanType(notification), getSubType(notification), getAction(notification));

		checkAndPropagateParentTraceId(span, notification);

		setSpanDetails(span, notification);

		String spanId = getSpanId(notification);

		transactionStore.addSpan(transactionId, spanId, span);
	}

	private static void checkAndPropagateParentTraceId(Span span, MessageProcessorNotification notification) {

		// Propagate trace.id through HTTP
		if (HttpTracingUtils.isHttpRequester(notification))
			HttpTracingUtils.propagateTraceIdHeader(span, notification);

	}

	public static String getSpanId(MessageProcessorNotification notification) {
		return notification.getInfo().getComponent().getLocation().getLocation();
	}

	private static Transaction getCurrentTransactionAndLog(String transactionId) {
		logger.debug("Could not find transaction " + transactionId + ". Using currentTransaction.");
		return ElasticApm.currentTransaction();
	}

	/*
	 * Populate Span details at creation time
	 */
	private static void setSpanDetails(Span span, MessageProcessorNotification notification) {
		// TODO Capture event properties
		// TODO Capture flow variables

		span.setName(getStepName(notification));

//		span.setStartTimestamp(getTimestamp(notification));

	}

	/*
	 * Get Step name
	 */
	public static String getStepName(MessageProcessorNotification notification) {
		Component component = notification.getComponent();
		String nameParam = component.getAnnotation(new QName(DOC_NAMESPACE, DOC_NAME)).toString();

		if (nameParam == null)
			return UNNAMED;

		return nameParam;
	}

	/*
	 * Get Span action
	 */
	private static String getAction(MessageProcessorNotification notification) {
		// Action = Span type
		return getSpanType(notification);
	}

	/*
	 * Get Span subtype
	 */
	private static String getSubType(MessageProcessorNotification notification) {

		// return const value
		return SUBTYPE;
	}

	/*
	 * Get Span type
	 */
	private static String getSpanType(MessageProcessorNotification notification) {
		Component component = notification.getComponent();
		String nameParam = component.getLocation().getComponentIdentifier().getIdentifier().getName();
		if (nameParam == null)
			return UNTYPED;

		return nameParam;
	}

	/*
	 * Get transactionId that is used to correlate Spans and Transactions. Comes
	 * from correlationId in the Mule event.
	 */
	private static String getTransactionId(MessageProcessorNotification notification) {
		return notification.getEvent().getCorrelationId();
	}

	/*
	 * End Span
	 */
	public static void endSpan(TransactionStore transactionStore, MessageProcessorNotification notification) {

		Span span = transactionStore.retrieveSpan(getTransactionId(notification), getSpanId(notification))
				.orElseGet(() -> ElasticApm.currentSpan());

		setFinalDetails(span, notification);

//		span.end(getTimestamp(notification));
		span.end();
	}

//	private static long getTimestamp(MessageProcessorNotification notification) {
//		long timestamp = notification.getTimestamp() * 1_000;
//		return timestamp;
//	}

	private static void setFinalDetails(Span span, MessageProcessorNotification notification) {
		// Noop currently
		// TODO: Populate output properties
		// TODO: Populate changed flowVars
		// TODO: Populate response body (if it is necessary).

	}

	/*
	 * Get the flow name
	 */
	public static String getFlowName(ComponentLocation location) {
		return location.getLocation().split("/")[0];
	}

}
