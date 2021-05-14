package com.avioconsulting.opentelemetry.spans;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.core.api.event.CoreEvent;

import co.elastic.apm.api.Transaction;
import io.opentelemetry.api.trace.Span;

/*
 * Store for transactions inflight.
 */
public class TransactionStore {

	private class TransactionAndSpans {

		private Transaction tx;
		public Map<String, Span> spMap = new ConcurrentHashMap<>();

		public TransactionAndSpans(Transaction tx) {
			this.tx = tx;
		}

		public Transaction getTransaction() {
			return this.tx;
		}

		public void setTransaction(Transaction tx) {
			this.tx = tx;
		}

		}

	// Possible concurrent access to the same transaction.
	private Map<String, TransactionAndSpans> txMap = new ConcurrentHashMap<String, TransactionAndSpans>();

	public boolean isTransactionPresent(String transactionId) {
		return txMap.containsKey(transactionId);
	}

	public void storeTransaction(String transactionId, Transaction transaction) {
		txMap.put(transactionId, new TransactionAndSpans(transaction));
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
	public Optional<Transaction> retrieveTransaction(String transactionId) {

		TransactionAndSpans remove = txMap.remove(transactionId);

		if (remove == null)
			return Optional.empty();

		return Optional.of(remove.getTransaction());
	}

	/*
	 * Get the transaction without removing it from the store.
	 */
	public Optional<Transaction> getTransaction(String transactionId) {
		TransactionAndSpans transactionAndSpans = txMap.get(transactionId);

		if (transactionAndSpans == null)
			return Optional.empty();

		return Optional.ofNullable(transactionAndSpans.getTransaction());
	}

	public void addSpan(String transactionId, String spanId, Span span) {
		Map<String, Span> spMap = txMap.get(transactionId).spMap;

		synchronized (spMap) {
			spMap.put(spanId, span);
		}
	}

	public Span retrieveSpan(String transactionId, String spanId) {
		TransactionAndSpans transactionAndSpans = txMap.get(transactionId);


		return transactionAndSpans.spMap.remove(spanId);
	}

//	public void updateTransaction(String transactionId, ApmTransaction transaction2) {
//		Optional<Transaction> transaction = getTransaction(transactionId);

	//	transaction.ifPresent((x) -> txMap.get(transactionId).setTransaction(transaction2)

//		);
	//}

}
