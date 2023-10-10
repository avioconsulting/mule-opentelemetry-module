package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
    Optional<Transaction> transaction = getTransaction(transactionId);
    if (transaction.isPresent()) {
      LOGGER.trace(
          "Start transaction {} for flow '{}' - Adding to existing transaction",
          transactionId,
          rootFlowName);
      transaction.get().getRootFlowSpan().addProcessorSpan(null, rootFlowName, rootFlowSpan);
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
    getTransaction(transactionId)
        .map(Transaction::getRootFlowSpan)
        .map(FlowSpan::getSpan).ifPresent(span -> span.setAllAttributes(builder.build()));
  }

  private Optional<Transaction> getTransaction(String transactionId) {
    return Optional.ofNullable(transactionMap.get(transactionId));
  }

  public Context getTransactionContext(String transactionId) {
    return getTransaction(transactionId)
        .map(Transaction::getRootFlowSpan)
        .map(FlowSpan::getSpan)
        .map(s -> s.storeInContext(Context.current()))
        .orElseGet(Context::current);
  }

  @Override
  public Context getTransactionContext(String transactionId, ComponentLocation componentLocation) {
    if (componentLocation == null)
      return getTransactionContext(transactionId);
    Optional<Context> context = getTransaction(transactionId)
        .map(Transaction::getRootFlowSpan)
        .flatMap(f -> f.findSpan(componentLocation.getLocation()))
        .map(s -> s.storeInContext(Context.current()));
    return context
        .orElseGet(() -> getTransactionContext(transactionId));
  }

  public String getTraceIdForTransaction(String transactionId) {
    Optional<Transaction> transaction = getTransaction(transactionId);
    return transaction.map(Transaction::getTraceId).orElse(null);
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
    getTransaction(transactionId)
        .ifPresent(
            transaction -> {
              if (transaction.getRootFlowName().equals(flowName)) {
                Transaction removed = transactionMap.remove(transactionId);
                endSpan.accept(removed.getRootFlowSpan().getSpan());
              } else {
                transaction.getRootFlowSpan().findSpan(flowName)
                    .ifPresent(endSpan);
              }
            });
  }

  @Override
  public void addProcessorSpan(String transactionId, String containerName, String location, SpanBuilder spanBuilder) {
    getTransaction(transactionId)
        .ifPresent(
            transaction -> {
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
            });
  }

  @Override
  public void endProcessorSpan(
      String transactionId, String location, Consumer<Span> spanUpdater, Instant endTime) {
    LOGGER.trace(
        "Ending Processor span of transaction {} for location '{}'",
        transactionId,
        location);
    getTransaction(transactionId)
        .ifPresent(
            transaction -> transaction
                .getRootFlowSpan()
                .endProcessorSpan(location, spanUpdater, endTime));
  }
}
