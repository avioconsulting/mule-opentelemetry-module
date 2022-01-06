package com.avioconsulting.mule.opentelemetry.spans;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.core.api.event.CoreEvent;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Store for transactions inflight.
 */
public class TransactionStore {
	private static Logger logger = LoggerFactory.getLogger(TransactionStore.class);


	private class FlowAndSpans {

		private Span flowSpan;
		public Map<String, Span> spanMap = new ConcurrentHashMap<>();

		public FlowAndSpans(Span flowSpan) {
			this.flowSpan = flowSpan;
		}

		public Span getFlowSpan() {
			return flowSpan;
		}
	}

	// Possible concurrent access to the same transaction.
	private Map<String, FlowAndSpans> txMap = new ConcurrentHashMap<>();

	public boolean isTransactionPresent(String transactionId) {
		return txMap.containsKey(transactionId);
	}

	public void storeTransaction(String transactionId, Span span) {
		txMap.put(transactionId, new FlowAndSpans(span));
	}

	public static boolean isFirstEvent(TransactionStore transactionStore, PipelineMessageNotification notification) {
		return !transactionStore.isTransactionPresent(getTransactionId(notification).get());
	}
	/*
	 * Retrieve the transactionId by getting the attached event to pipeline
	 * notification or exception
	 */
	private static Optional<String> getTransactionId(PipelineMessageNotification notification) {

		Event event = notification.getEvent();

		if (event != null)
			return Optional.of(event.getCorrelationId());

		// If the event == null, this must be the case of an exception and the original
		// event is attached under processedEvent in the exception.
		Exception e = notification.getInfo().getException();

		// This is a really ugly hack to get around the fact that
		// org.mule.runtime.core.internal.exception.MessagingException class is not
		// visible in the current classloader and there is no documentation to explain
		// how to access objects of this class and why the hell it is internal and is
		// not part of the API.
		// TODO: raise why org.mule.runtime.core.internal.exception.MessagingException
		// is not part of the API with Mule support.
		Field f = null;
		CoreEvent iWantThis = null;
		try {
			f = e.getClass().getDeclaredField("processedEvent");
		} catch (NoSuchFieldException | SecurityException e1) {
			e1.printStackTrace();
		} // NoSuchFieldException
		f.setAccessible(true);
		try {
			iWantThis = (CoreEvent) f.get(e);
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		} // IllegalAccessException

		if (iWantThis != null) {
			String correlationId = iWantThis.getCorrelationId();
			return Optional.of(correlationId);
		}

		return Optional.empty();
	}

	/*
	 * Retrieve and remove the transaction from the store.
	 */
	public void endTransaction(String transactionId) {

		FlowAndSpans remove = txMap.remove(transactionId);

		if (remove != null) {
			remove.spanMap.forEach(
					(s, span) -> span.end()
			);
			remove.flowSpan.end();
		}
	}

	/*
	 * Get the transaction without removing it from the store.
	 */
	public Optional<FlowAndSpans> getTransaction(String transactionId) {
		FlowAndSpans span = txMap.get(transactionId);

		if (span == null)
			return Optional.empty();

		return Optional.ofNullable(span);
	}

	public void addSpan(String transactionId, String spanId, SpanBuilder span) {
		FlowAndSpans parentSpan = txMap.get(transactionId);
		Span value = span.setParent(Context.current().with(parentSpan.getFlowSpan())).startSpan();
		logger.info(value.getSpanContext().getTraceId());
		parentSpan.spanMap.put(spanId, value);
	}

	public Span getSpan(String transactionId, String spanId) {
		FlowAndSpans parentSpan = txMap.get(transactionId);
		return parentSpan.spanMap.get(spanId);
	}

//	public void updateTransaction(String transactionId, ApmTransaction transaction2) {
//		Optional<Transaction> transaction = getTransaction(transactionId);

	//	transaction.ifPresent((x) -> txMap.get(transactionId).setTransaction(transaction2)

//		);
	//}

}
