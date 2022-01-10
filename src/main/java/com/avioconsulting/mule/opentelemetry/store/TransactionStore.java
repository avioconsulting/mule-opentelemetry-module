package com.avioconsulting.mule.opentelemetry.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import java.util.function.Consumer;

/**
 * Transaction store to managing transactions locally or on remote persistent storage.
 */
public interface TransactionStore {

    /**
     * Start a new transaction. This usually happens when a new source flow starts.
     * If the transaction with `transactionId` already exists but for different rootFlowName, then it is possible that
     * new rootFlowSpan is a child flow invocation. In that case, span may be added to an existing transaction as a span.
     * @param transactionId A unique transaction id within the context of an application. Eg. Correlation id.
     * @param rootFlowName Name of the flow requesting to start transaction.
     * @param rootFlowSpan @{@link SpanBuilder} for building the root span.
     */
    void startTransaction(String transactionId, String rootFlowName, SpanBuilder rootFlowSpan);

    /**
     * End a transaction represented by provided transaction id and rootFlowName, if exists.
     * @param transactionId A unique transaction id within the context of an application. Eg. Correlation id.
     * @param rootFlowName Name of the flow requesting to start transaction.
     */
    void endTransaction(String transactionId, String rootFlowName);

    /**
     * End a transaction represented by provided transaction id and rootFlowName, if exists. @{@link Consumer<Span>} parameter allows updating the Span before ending.
     * This is useful in scenarios like setting processing status code to error.
     *
     * Here is an example of setting Error when processor execution fails.
     *
     * <code>
     *     transactionStore.endTransaction(traceComponent.getTransactionId(), traceComponent.getName(), rootSpan -> {
     *                 if(notification.getException() != null) {
     *                     rootSpan.setStatus(StatusCode.ERROR, notification.getException().getMessage());
     *                     rootSpan.recordException(notification.getException());
     *                 }
     *             });
     * </code>
     * @param transactionId A unique transaction id within the context of an application. Eg. Correlation id.
     * @param rootFlowName Name of the flow requesting to start transaction.
     * @param spanUpdater  @{@link Consumer<Span>} to allow updating Span before ending.
     */
    void endTransaction(String transactionId, String rootFlowName, Consumer<Span> spanUpdater);
    /**
     * Add a new processor span under an existing transaction.
     * @param transactionId
     * @param location
     * @param spanBuilder
     */
    void addProcessorSpan(String transactionId, String location, SpanBuilder spanBuilder);

    /**
     * End an existing span under an existing transaction.
     * @param transactionId
     * @param location
     */
    void endProcessorSpan(String transactionId, String location);

    /**
     * End an existing span under an existing transaction. @{@link Consumer<Span>} parameter allows updating the Span before ending.
     * This is useful in scenarios like setting processing status code to error.
     *
     * Here is an example of setting Error when processor execution fails.
     * <code>
     *     transactionStore.endProcessorSpan(traceComponent.getTransactionId(), traceComponent.getLocation(), s -> {
     *                 if(notification.getEvent().getError().isPresent()) {
     *                     Error error = notification.getEvent().getError().get();
     *                     s.setStatus(StatusCode.ERROR, error.getDescription());
     *                     s.recordException(error.getCause());
     *                 }
     *             });
     *
     * </code>
     * @param transactionId
     * @param location
     * @param spanUpdater @{@link Consumer<Span>} to allow updating Span before ending.
     */
    void endProcessorSpan(String transactionId, String location, Consumer<Span> spanUpdater);

}
