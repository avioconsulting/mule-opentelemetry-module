package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory {@link TransactionStore}. This implementation uses
 * in-memory {@link java.util.Map} to
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
      final String transactionId, final String rootFlowName, SpanBuilder rootFlowSpan) {
    Transaction transaction = getTransaction(transactionId);
    if (transaction != null) {
      LOGGER.trace(
          "Start transaction {} for flow '{}' - Adding to existing transaction",
          transactionId,
          rootFlowName);
      transaction.getRootFlowSpan().addProcessorSpan(null, rootFlowName, rootFlowSpan);
    } else {
      Span span = rootFlowSpan.startSpan();
      LOGGER.trace(
          "Start transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          transactionId,
          rootFlowName,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
      transactionMap.put(
          transactionId,
          new Transaction(transactionId, span.getSpanContext().getTraceId(), rootFlowName,
              new FlowSpan(rootFlowName, span)));
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

  private Context getTransactionContext(Transaction transaction) {
    return transaction == null ? Context.current()
        : transaction.getRootFlowSpan().getSpan().storeInContext(Context.current());
  }

  @Override
  public Context getTransactionContext(String transactionId, ComponentLocation componentLocation) {
    Transaction transaction = getTransaction(transactionId);
    if (componentLocation == null)
      return getTransactionContext(transaction);
    Span span = null;
    if (transaction != null
        && ((span = transaction.getRootFlowSpan().findSpan(componentLocation.getLocation())) != null)) {
      return span.storeInContext(Context.current());
    } else {
      return getTransactionContext(transaction);
    }
  }

  public String getTraceIdForTransaction(String transactionId) {
    return transactionMap.containsKey(transactionId) ? getTransaction(transactionId).getTraceId() : null;
  }

  @Override
  public void endTransaction(
      String transactionId,
      String flowName,
      Consumer<Span> spanUpdater,
      Instant endTime) {
    LOGGER.trace("End transaction {} for flow '{}'", transactionId, flowName);
    Consumer<Span> endSpan = (span) -> {
      if (spanUpdater != null)
        spanUpdater.accept(span);
      span.end(endTime);
      LOGGER.trace(
          "Ended transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          transactionId,
          flowName,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
    };
    Transaction transaction = getTransaction(transactionId);
    if (transaction != null) {
      if (transaction.getRootFlowName().equals(flowName)) {
        Transaction removed = transactionMap.remove(transactionId);
        endSpan.accept(removed.getRootFlowSpan().getSpan());
      } else {
        Span span = transaction.getRootFlowSpan().findSpan(flowName);
        if (span != null)
          endSpan.accept(span);
      }
    }
  }

  @Override
  public void addProcessorSpan(String transactionId, String containerName, String location, SpanBuilder spanBuilder) {
    Transaction transaction = getTransaction(transactionId);

    if (transaction != null) {
      LOGGER.trace(
          "Adding Processor span to transaction {} for location '{}'",
          transactionId,
          location);
      Span span = transaction
          .getRootFlowSpan()
          .addProcessorSpan(containerName, location, spanBuilder);
      LOGGER.trace(
          "Adding Processor span to transaction {} for locator span '{}': OT SpanId {}, TraceId {}",
          transactionId,
          location,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
    }
  }

  @Override
  public void endProcessorSpan(
      String transactionId, String location, Consumer<Span> spanUpdater, Instant endTime) {
    LOGGER.trace(
        "Ending Processor span of transaction {} for location '{}'",
        transactionId,
        location);
    Transaction transaction = getTransaction(transactionId);

    if (transaction != null) {
      transaction
          .getRootFlowSpan()
          .endProcessorSpan(location, spanUpdater, endTime);
    }
  }
}
