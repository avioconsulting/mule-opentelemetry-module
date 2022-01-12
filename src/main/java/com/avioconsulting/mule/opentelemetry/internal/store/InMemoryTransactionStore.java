package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
            LOGGER.trace("Start transaction {} for flow '{}' - Adding to existing transaction", transactionId, rootFlowName);
            transaction.get().getRootFlowSpan().addProcessorSpan(rootFlowName, rootFlowSpan);
        } else {
            Span span = rootFlowSpan.startSpan();
            LOGGER.trace("Start transaction {} for flow '{}': OT SpanId {}, TraceId {}", transactionId, rootFlowName, span.getSpanContext().getSpanId(), span.getSpanContext().getTraceId());
            transactionMap.put(transactionId, new Transaction(transactionId, rootFlowName, new FlowSpan(rootFlowName, span)));
        }
    }

    private Optional<Transaction> getTransaction(String transactionId) {
        return Optional.ofNullable(transactionMap.get(transactionId));
    }

    public Context getTransactionContext(String transactionId){
        return getTransaction(transactionId).map(Transaction::getRootFlowSpan).map(FlowSpan::getSpan).map(s -> s.storeInContext(Context.current())).orElse(Context.current());
    }

    @Override
    public void endTransaction(String transactionId, String rootFlowName, Consumer<Span> spanUpdater, Instant endTime) {
        LOGGER.trace("End transaction {} for flow '{}'", transactionId, rootFlowName);
        getTransaction(transactionId)
                .filter(t -> rootFlowName.equalsIgnoreCase(t.getRootFlowName()))
                .ifPresent(transaction -> {
                    Transaction removed = transactionMap.remove(transactionId);
                    Span rootSpan = removed.getRootFlowSpan().getSpan();
                    if(spanUpdater != null) spanUpdater.accept(rootSpan);
                    removed.getRootFlowSpan().end(endTime);
                    LOGGER.trace("Ended transaction {} for flow '{}': OT SpanId {}, TraceId {}", transactionId, rootFlowName, rootSpan.getSpanContext().getSpanId(), rootSpan.getSpanContext().getTraceId());
                });
    }

    @Override
    public void addProcessorSpan(String transactionId, String location, SpanBuilder spanBuilder) {
        getTransaction(transactionId).ifPresent(transaction -> {
            LOGGER.trace("Adding Processor span to transaction {} for location '{}'", transactionId, location);
            Span span = transaction.getRootFlowSpan().addProcessorSpan(location, spanBuilder);
            LOGGER.trace("Adding Processor span to transaction {} for locator span '{}': OT SpanId {}, TraceId {}", transactionId, location, span.getSpanContext().getSpanId(), span.getSpanContext().getTraceId());
        });
    }

    @Override
    public void endProcessorSpan(String transactionId, String location, Consumer<Span> spanUpdater, Instant endTime) {
        LOGGER.trace("Ending Processor span of transaction {} for location '{}'", transactionId, location);
        getTransaction(transactionId)
                .ifPresent(transaction -> transaction.getRootFlowSpan().endProcessorSpan(location, spanUpdater, endTime));
    }

}
