package com.avioconsulting.mule.opentelemetry.store;

import io.opentelemetry.api.trace.SpanBuilder;

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
     * Add a new processor span under an existing transaction.
     * @param transactionId
     * @param location
     * @param spanBuilder
     */
    void addProcessorSpan(String transactionId, String location, SpanBuilder spanBuilder);

    /**
     * End a existing span under an existing transaction.
     * @param transactionId
     * @param location
     */
    void endProcessorSpan(String transactionId, String location);

}
