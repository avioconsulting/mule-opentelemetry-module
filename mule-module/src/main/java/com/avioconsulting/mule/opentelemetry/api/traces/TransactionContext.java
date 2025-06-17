package com.avioconsulting.mule.opentelemetry.api.traces;

import com.avioconsulting.mule.opentelemetry.api.util.EncodingUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.context.Context;

import java.util.Objects;

public class TransactionContext {
  private Context context = Context.current();

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

  /**
   * This method returns the String formatted Long value of span id. See
   * {@link TransactionContext#getSpanId()}.
   * Example: Span Id - "53f9aa133a283c1a"
   * Long Low Id - "6051054573905787930"
   */
  private String spanIdLong = "0";

  /**
   * This method returns the String formatted Long value of the Low part of the
   * trace id. See {@link TransactionContext#getTraceId()}.
   * Example: Trace Id - fbc14552c62fbabc6a4bc6817cd983ce
   * High-part - "fbc14552c62fbabc"
   * Low-part - "6a4bc6817cd983ce"
   * Long Low Id - "7659433850721371086"
   */
  private String traceIdLongLowPart = "0";

  public static TransactionContext of(Span span) {
    TransactionContext transactionContext = new TransactionContext()
        .setContext(span.storeInContext(Context.current()))
        .setSpanId(span.getSpanContext().getSpanId())
        .setTraceId(span.getSpanContext().getTraceId());
    if (SpanId.isValid(transactionContext.getSpanId())) {
      transactionContext.setSpanIdLong(EncodingUtil.longFromBase16Hex(transactionContext.getSpanId()));
    }
    if (TraceId.isValid(transactionContext.getTraceId())) {
      transactionContext.setTraceIdLongLowPart(EncodingUtil.traceIdLong(transactionContext.getTraceId())[1]);
    }
    return transactionContext;
  }

  public static TransactionContext current() {
    return new TransactionContext();
  }

  public Context getContext() {
    return context;
  }

  public TransactionContext setContext(Context context) {
    this.context = context;
    return this;
  }

  public String getSpanId() {
    return spanId;
  }

  public TransactionContext setSpanId(String spanId) {
    this.spanId = spanId;
    return this;
  }

  public String getTraceId() {
    return traceId;
  }

  public TransactionContext setTraceId(String traceId) {
    this.traceId = traceId;
    return this;
  }

  public String getSpanIdLong() {
    return spanIdLong;
  }

  public TransactionContext setSpanIdLong(String spanIdLong) {
    this.spanIdLong = spanIdLong;
    return this;
  }

  public String getTraceIdLongLowPart() {
    return traceIdLongLowPart;
  }

  public TransactionContext setTraceIdLongLowPart(String traceIdLongLowPart) {
    this.traceIdLongLowPart = traceIdLongLowPart;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    TransactionContext that = (TransactionContext) o;
    return Objects.equals(getContext(), that.getContext()) && Objects.equals(getSpanId(), that.getSpanId())
        && Objects.equals(getTraceId(), that.getTraceId())
        && Objects.equals(getSpanIdLong(), that.getSpanIdLong())
        && Objects.equals(getTraceIdLongLowPart(), that.getTraceIdLongLowPart());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getContext(), getSpanId(), getTraceId(), getSpanIdLong(), getTraceIdLongLowPart());
  }
}
