package com.avioconsulting.mule.opentelemetry.api.traces;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.store.Transaction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.*;

public class TransactionContext {
  private Context context = Context.current();
  private final Map<String, Object> traceContextMap = new java.util.HashMap<>();
  /**
   * Get the 16-hex-character lowercase string span id
   */
  private String spanId = SpanId.getInvalid();
  /**
   * Trace id is a 32-hex-character lowercase string built with two
   * 16-hex-character lowercase strings,
   * each representing a long value encoded to hex.
   * First 16 characters represent High-long and the last 16 characters represent
   * Low-long values making the trace id.
   */
  private String traceId = TraceId.getInvalid();

  public static TransactionContext of(Span span, Transaction transaction) {
    TransactionContext transactionContext = new TransactionContext();
    transactionContext.context = span.storeInContext(Context.current());
    transactionContext.spanId = span.getSpanContext().getSpanId();
    transactionContext.traceId = span.getSpanContext().getTraceId();
    transactionContext.traceContextMap.put(TRACE_TRANSACTION_ID, transaction.getTransactionId());
    transactionContext.traceContextMap.put(TransactionStore.traceId, transactionContext.getTraceId());
    transactionContext.traceContextMap.put(TransactionStore.spanId, transactionContext.getSpanId());
    return transactionContext;
  }

  public static TransactionContext current() {
    return new TransactionContext();
  }

  public Context getContext() {
    return context;
  }

  public Map<String, Object> getTraceContextMap() {
    return new HashMap<>(traceContextMap);
  }

  public String getSpanId() {
    return spanId;
  }

  public String getTraceId() {
    return traceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    TransactionContext that = (TransactionContext) o;
    return Objects.equals(getContext(), that.getContext()) && Objects.equals(getSpanId(), that.getSpanId())
        && Objects.equals(getTraceId(), that.getTraceId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getContext(), getSpanId(), getTraceId());
  }
}
