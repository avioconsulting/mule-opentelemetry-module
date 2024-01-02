package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.util.Objects;

public class TransactionContext {
  private Context context;
  private String spanId;

  public static TransactionContext of(Span span) {
    return new TransactionContext()
        .setContext(span.storeInContext(Context.current()))
        .setSpanId(span.getSpanContext().getSpanId());
  }

  public static TransactionContext current() {
    return new TransactionContext().setContext(Context.current());
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    TransactionContext that = (TransactionContext) o;
    return Objects.equals(getContext(), that.getContext()) && Objects.equals(getSpanId(), that.getSpanId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getContext(), getSpanId());
  }
}
