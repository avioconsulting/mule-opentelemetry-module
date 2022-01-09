package com.avioconsulting.mule.opentelemetry.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory @{@link TransactionStore}. This implementation uses in-memory @{@link java.util.Map} to store transactions
 * and related processor spans. Transactions are kept in memory until they end.
 */
public class InMemoryTransactionStore implements TransactionStore {
    private static TransactionStore service;
    private final ConcurrentHashMap<String, Transaction> transactionMap = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryTransactionStore.class);

    public static synchronized TransactionStore getInstance() {
        if (service == null) {
            service = new InMemoryTransactionStore();
        }
        return service;
    }

    @Override
    public void startTransaction(final String transactionId, final String rootFlowName, SpanBuilder rootFlowSpan) {
        Optional<Transaction> transaction = getTransaction(transactionId);
        if(transaction.isPresent()) {
            LOGGER.debug("Start transaction {} for flow '{}' - Adding to existing transaction", transactionId, rootFlowName);
            transaction.get().getRootFlowSpan().addProcessorSpan(rootFlowName, rootFlowSpan);
        } else {
            Span span = rootFlowSpan.startSpan();
            LOGGER.debug("Start transaction {} for flow '{}': OT SpanId {}, TraceId {}", transactionId, rootFlowName, span.getSpanContext().getSpanId(), span.getSpanContext().getTraceId());
            transactionMap.put(transactionId, new Transaction(transactionId, rootFlowName, new FlowSpan(rootFlowName, span)));
        }
    }

    private Optional<Transaction> getTransaction(String transactionId) {
        return Optional.ofNullable(transactionMap.get(transactionId));
    }

    @Override
    public void endTransaction(final String transactionId, final String rootFlowName) {
        LOGGER.debug("End transaction {} for flow '{}'", transactionId, rootFlowName);
        getTransaction(transactionId)
                .filter(t -> rootFlowName.equalsIgnoreCase(t.getRootFlowName()))
                .ifPresent(transaction -> {
                    Transaction removed = transactionMap.remove(transactionId);
                    removed.getRootFlowSpan().end();
                    Span rootSpan = removed.getRootFlowSpan().getSpan();
                    LOGGER.debug("Ended transaction {} for flow '{}': OT SpanId {}, TraceId {}", transactionId, rootFlowName, rootSpan.getSpanContext().getSpanId(), rootSpan.getSpanContext().getTraceId());
                });
    }

    @Override
    public void addProcessorSpan(String transactionId, String location, SpanBuilder spanBuilder) {
        getTransaction(transactionId).ifPresent(transaction -> {
            LOGGER.debug("Adding Processor span to transaction {} for location '{}'", transactionId, location);
            Span span = transaction.getRootFlowSpan().addProcessorSpan(location, spanBuilder);
            LOGGER.debug("Adding Processor span to transaction {} for locator span '{}': OT SpanId {}, TraceId {}", transactionId, location, span.getSpanContext().getSpanId(), span.getSpanContext().getTraceId());
        });
    }

    @Override
    public void endProcessorSpan(String transactionId, String location) {
        LOGGER.debug("Ending Processor span of transaction {} for location '{}'", transactionId, location);
        getTransaction(transactionId)
                .ifPresent(transaction -> transaction.getRootFlowSpan().endProcessorSpan(location));
    }
}
