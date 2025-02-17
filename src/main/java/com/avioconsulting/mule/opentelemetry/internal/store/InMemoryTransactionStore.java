package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.traces.TransactionContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory {@link TransactionStore}. This implementation uses
 * in-memory {@link Map} to
 * store transactions and related processor spans. Transactions are kept in
 * memory until they end.
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
  public void startTransaction(
      final TraceComponent traceComponent, final String rootFlowName, SpanBuilder rootFlowSpanBuilder) {
    final String transactionId = traceComponent.getTransactionId();
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    if (transaction != null) {
      LOGGER.trace(
          "Start transaction {} for flow '{}' - Adding to existing transaction",
          transactionId,
          rootFlowName);
      transaction.getRootFlowSpan().addChildFlow(traceComponent, rootFlowSpanBuilder);
    } else {
      Span span = rootFlowSpanBuilder.startSpan();
      LOGGER.trace(
          "Start transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          transactionId,
          rootFlowName,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
      transactionMap.put(
          transactionId,
          new Transaction(traceComponent.getTransactionId(), span.getSpanContext().getTraceId(), rootFlowName,
              new FlowSpan(rootFlowName, span, transactionId)
                  .setTags(traceComponent.getTags())
                  .setRootSpanName(traceComponent.getSpanName()),
              traceComponent.getStartTime()));
    }
  }

  @Override
  public void addTransactionTags(String transactionId, String tagPrefix, Map<String, String> tags) {
    AttributesBuilder builder = Attributes.builder();
    String format = "%s.%s";
    tags.forEach((k, v) -> builder.put(String.format(format, tagPrefix, k), v));
    Transaction transaction = getTransaction(transactionId);
    Span span = transaction.getRootFlowSpan().getSpan();
    if (span != null) {
      span.setAllAttributes(builder.build());
    }
  }

  private Transaction getTransaction(String transactionId) {
    return transactionMap.get(transactionId);
  }

  private TransactionContext getTransactionContext(Transaction transaction) {
    return transaction == null ? TransactionContext.current()
        : TransactionContext.of(transaction.getRootFlowSpan().getSpan());
  }

  @Override
  public TransactionContext getTransactionContext(String transactionId, String componentLocation) {
    Transaction transaction = getTransaction(transactionId);
    if (componentLocation == null)
      return getTransactionContext(transaction);
    ProcessorSpan processorSpan = null;
    if (transaction != null
        && ((processorSpan = transaction.getRootFlowSpan()
            .findSpan(componentLocation)) != null)) {
      return TransactionContext.of(processorSpan.getSpan());
    } else {
      return getTransactionContext(transaction);
    }
  }

  public String getTraceIdForTransaction(String transactionId) {
    return transactionMap.containsKey(transactionId) ? getTransaction(transactionId).getTraceId() : null;
  }

  @Override
  public TransactionMeta endTransaction(
      TraceComponent traceComponent,
      Consumer<Span> spanUpdater) {
    Consumer<Span> endSpan = (span) -> {
      if (spanUpdater != null)
        spanUpdater.accept(span);
      span.end(traceComponent.getEndTime());
      LOGGER.trace(
          "Ended transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          traceComponent,
          traceComponent.getName(),
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
    };
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    TransactionMeta transactionMeta = transaction;
    if (transaction != null) {
      if (transaction.getRootFlowName().equals(traceComponent.getName())) {
        LOGGER.trace("Marking the end time of transaction {} from map for {} - Context Id {}",
            traceComponent.getTransactionId(),
            traceComponent.getName(), traceComponent.getEventContextId());
        transaction.endRootFlow(traceComponent, endSpan);
      } else {
        // This is a flow invoked by a flow-ref and not the main flow
        transactionMeta = transaction.getRootFlowSpan().endChildFlow(traceComponent, endSpan);
      }
      if (transaction.hasEnded()) {
        LOGGER.trace("Removed transaction {} from map for {} - Context Id {}",
            traceComponent.getTransactionId(),
            traceComponent.getName(), traceComponent.getEventContextId());
        transactionMap.remove(transaction.getTransactionId());
      }
    } else {
      LOGGER.trace("No transaction found for transaction {}", traceComponent.getTransactionId());
    }
    if (transactionMeta == null) {
      LOGGER.trace("No transaction meta found for {} ", traceComponent);
    }
    return transactionMeta;
  }

  @Override
  public SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    if (transaction == null) {
      return null;
    }
    LOGGER.trace(
        "Adding Processor span to transaction {} for location '{}'",
        traceComponent.getTransactionId(),
        traceComponent.getLocation());
    SpanMeta span = transaction
        .getRootFlowSpan()
        .addProcessorSpan(containerName, traceComponent, spanBuilder);
    LOGGER.trace(
        "Adding Processor span to transaction {} for locator span '{}': OT SpanId {}, TraceId {}",
        traceComponent.getTransactionId(),
        traceComponent.getLocation(),
        span.getSpanId(),
        span.getTraceId());
    return span;
  }

  @Override
  public SpanMeta endProcessorSpan(
      String transactionId, TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    LOGGER.trace(
        "Ending Processor span of transaction {} for location '{}'",
        transactionId,
        traceComponent);
    Transaction transaction = getTransaction(transactionId);

    if (transaction == null) {
      return null;
    }
    return transaction
        .getRootFlowSpan()
        .endProcessorSpan(traceComponent, spanUpdater, endTime);
  }
}
