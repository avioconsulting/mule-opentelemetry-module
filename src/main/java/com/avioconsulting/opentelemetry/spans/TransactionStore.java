package com.avioconsulting.opentelemetry.spans;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
