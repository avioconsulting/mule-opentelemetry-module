package ARCHIVE.co.elastic.apm.mule4.agent.exception;

import java.util.Optional;

import ARCHIVE.co.elastic.apm.mule4.agent.transaction.ApmTransaction;
import org.mule.runtime.api.notification.ExceptionNotification;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import ARCHIVE.co.elastic.apm.mule4.agent.transaction.TransactionStore;

/* 
 * Handling of Exceptions thrown by flow steps.
 */
public class ExceptionUtils {
	private static final String ERROR_FLOW = "ERROR_FLOW";
	private static final String ERROR_STEP = "ERROR_STEP";

	public static void captureException(TransactionStore transactionStore, ExceptionNotification notification) {

		String transactionId = getTransactionId(notification);

		Optional<Transaction> transaction = Optional.empty();

		// Ensure the transaction is not being updated from multiple threads.
		synchronized (transaction) {

			// Ensure we are only attaching the Exception to transaction once, since
			// rethrowing it causes this methid to be invoked multiple times.
			transaction = transactionStore.getTransaction(transactionId);

			ApmTransaction transaction2;
			if (!transaction.isPresent())
				transaction2 = new ApmTransaction(ElasticApm.currentTransaction());
			else
				transaction2 = (ApmTransaction) transaction.get();

			if (transaction2.hasException())
				return;

			// Capture the Exception details and store the transaction back.
			transaction2.captureException(getCause(notification));
			transaction2.addLabel(ERROR_STEP, getStepName(notification));
			transaction2.addLabel(ERROR_FLOW, getFlowName(notification));
			transaction2.setException();
			transactionStore.updateTransaction(transactionId, transaction2);
		}

	}

	private static String getTransactionId(ExceptionNotification notification) {
		return notification.getEvent().getCorrelationId();
	}

	private static String getFlowName(ExceptionNotification notification) {
		return notification.getComponent().getLocation().getRootContainerName();
	}

	private static String getStepName(ExceptionNotification notification) {
		return notification.getComponent().getLocation().getComponentIdentifier().getIdentifier().getName();
	}

	private static Throwable getCause(ExceptionNotification notification) {
		return notification.getException();
	}

}
