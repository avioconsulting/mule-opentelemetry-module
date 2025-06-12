package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.traces.TransactionContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionContextTest {

  @Test
  public void current() {
    TransactionContext transactionContext = TransactionContext.current();
    Assertions.assertThat(transactionContext)
        .extracting("spanId", "spanIdLong", "traceId", "traceIdLongLowPart")
        .containsOnly("0000000000000000", "0", "00000000000000000000000000000000", "0");
    Assertions.assertThat(transactionContext.getContext())
        .as("Current Context")
        .isNotNull();
  }

  @Test
  public void ofValidSpan() {
    Span span = mock(Span.class);
    SpanContext spanContext = mock(SpanContext.class);
    when(spanContext.getSpanId()).thenReturn("53f9aa133a283c1a");
    when(spanContext.getTraceId()).thenReturn("fbc14552c62fbabc6a4bc6817cd983ce");
    when(span.getSpanContext()).thenReturn(spanContext);
    when(span.storeInContext(any(Context.class))).thenReturn(Context.current());

    TransactionContext transactionContext = TransactionContext.of(span);
    Assertions.assertThat(transactionContext)
        .extracting("spanId", "spanIdLong", "traceId", "traceIdLongLowPart")
        .containsOnly("53f9aa133a283c1a", "6051054573905787930", "fbc14552c62fbabc6a4bc6817cd983ce",
            "7659433850721371086");
    Assertions.assertThat(transactionContext.getContext())
        .as("Current Context")
        .isNotNull();
  }

}