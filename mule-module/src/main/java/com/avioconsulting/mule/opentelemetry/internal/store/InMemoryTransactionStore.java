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

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.getBatchJobInstanceId;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.hasBatchJobInstanceId;

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
  private final TransactionProcessor transactionProcessor;

  private InMemoryTransactionStore(TransactionProcessor transactionProcessor) {
    this.transactionProcessor = transactionProcessor;
  }

  public static synchronized TransactionStore getInstance(TransactionProcessor transactionProcessor) {
    if (service == null) {
      service = new InMemoryTransactionStore(transactionProcessor);
    }
    return service;
  }

  public static void _resetForTesting() {
    service = null;
  }

  @Override
  public void startTransaction(
      final TraceComponent traceComponent, final String rootName, SpanBuilder spanBuilder) {
    final String transactionId = traceComponent.getTransactionId();
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    if (transaction != null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Start transaction {} for flow '{}' - Adding to existing transaction",
            transactionId,
            rootName);
      }
      transaction.addChildTransaction(traceComponent, spanBuilder);
    } else {
      boolean isBatchJob = traceComponent.getTags().containsKey(MULE_BATCH_JOB_NAME.getKey());
      if (isBatchJob) {
        startBatchTransaction(traceComponent, rootName, spanBuilder, transactionId);
      } else {
        startFlowTransaction(traceComponent, rootName, spanBuilder, transactionId);
      }
    }
  }

  private void startFlowTransaction(TraceComponent traceComponent, String rootName, SpanBuilder spanBuilder,
      String transactionId) {
    Span span = spanBuilder.startSpan();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Start Flow transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          transactionId,
          rootName,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
    }
    transactionMap.put(
        transactionId,
        new FlowTransaction(traceComponent.getTransactionId(), span.getSpanContext().getTraceId(),
            rootName,
            new FlowSpan(rootName, span, traceComponent),
            traceComponent.getStartTime()));
  }

  private void startBatchTransaction(TraceComponent traceComponent, String rootName, SpanBuilder spanBuilder,
      String transactionId) {
    Span span = spanBuilder.startSpan();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Start Batch transaction {} for flow '{}': OT SpanId {}, TraceId {}",
          transactionId,
          rootName,
          span.getSpanContext().getSpanId(),
          span.getSpanContext().getTraceId());
    }
    transactionMap.put(
        transactionId,
        new BatchTransaction(traceComponent.getTransactionId(), span.getSpanContext().getTraceId(),
            rootName, span, traceComponent, transactionProcessor::spanBuilder,
            transactionProcessor.getComponentRegistryService()));
  }

  @Override
  public void addTransactionTags(String transactionId, String tagPrefix, Map<String, String> tags) {
    AttributesBuilder builder = Attributes.builder();
    String format = "%s.%s";
    tags.forEach((k, v) -> builder.put(String.format(format, tagPrefix, k), v));
    Transaction transaction = getTransaction(transactionId);
    Span span = transaction.getTransactionSpan();
    if (span != null) {
      span.setAllAttributes(builder.build());
    }
  }

  private Transaction getTransaction(String transactionId) {
    return transactionMap.get(transactionId);
  }

  @Override
  public TransactionContext getTransactionContext(String transactionId, String componentLocation) {
    Transaction transaction = getTransaction(transactionId);
    if (componentLocation == null)
      return transaction.getTransactionContext();
    ProcessorSpan processorSpan = null;
    if (transaction != null
        && ((processorSpan = transaction
            .findSpan(componentLocation)) != null)) {
      return TransactionContext.of(processorSpan.getSpan(), transaction);
    } else {
      return transaction.getTransactionContext();
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
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Ended transaction {} for flow '{}': OT SpanId {}, TraceId {}",
            traceComponent,
            traceComponent.getName(),
            span.getSpanContext().getSpanId(),
            span.getSpanContext().getTraceId());
      }
    };
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    TransactionMeta transactionMeta = transaction;
    if (transaction != null) {
      if (transaction.getRootFlowName().equals(traceComponent.getName())) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Marking the end time of transaction {} from map for {} - Context Id {}",
              traceComponent.getTransactionId(),
              traceComponent.getName(), traceComponent.getEventContextId());
        }
        transaction.endRootSpan(traceComponent, endSpan);
      } else {
        // This is a flow invoked by a flow-ref and not the main flow
        transactionMeta = transaction.endChildTransaction(traceComponent, endSpan);
      }
      if (transaction.hasEnded()) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Removed transaction {} from map for {} - Context Id {}",
              traceComponent.getTransactionId(),
              traceComponent.getName(), traceComponent.getEventContextId());
        }
        transactionMap.remove(transaction.getTransactionId());
      }
    } else {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("No transaction found for transaction {}", traceComponent.getTransactionId());
      }
    }
    if (transactionMeta == null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("No transaction meta found for {} ", traceComponent);
      }
    } else if (traceComponent.getTags() != null && transactionMeta.getTags() != null) {
      transactionMeta.getTags().putAll(traceComponent.getTags());
    }
    return transactionMeta;
  }

  @Override
  public SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    Transaction transaction = getTransaction(traceComponent.getTransactionId());
    if (transaction == null && hasBatchJobInstanceId(traceComponent)) {
      String batchJobInstanceId = getBatchJobInstanceId(traceComponent);
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Looking for transaction for batch job instance id {} ", batchJobInstanceId);
      }
      transaction = getTransaction(batchJobInstanceId);
    }
    if (transaction == null) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("No transaction found for transaction {}. Map contains transactions for {}",
            traceComponent.getTransactionId(), transactionMap.keySet());
      }
      return null;
    }
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Adding Processor span to transaction {} for location '{}'",
          traceComponent.getTransactionId(),
          traceComponent.getLocation());
    }
    SpanMeta span = transaction
        .addProcessorSpan(containerName, traceComponent, spanBuilder);
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Adding Processor span to transaction {} for locator span '{}': OT SpanId {}, TraceId {}",
          traceComponent.getTransactionId(),
          traceComponent.getLocation(),
          span.getSpanId(),
          span.getTraceId());
    }
    return span;
  }

  @Override
  public SpanMeta endProcessorSpan(
      String transactionId, TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Ending Processor span of transaction {} for location '{}'",
          transactionId,
          traceComponent);
    }
    Transaction transaction = getTransaction(transactionId);

    if (transaction == null) {
      return null;
    }
    return transaction
        .endProcessorSpan(traceComponent, spanUpdater, endTime);
  }
}
