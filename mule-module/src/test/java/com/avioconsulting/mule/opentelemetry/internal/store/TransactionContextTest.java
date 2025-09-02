package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.traces.TransactionContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionContextTest {

  public static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] bytes = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
          + Character.digit(hex.charAt(i + 1), 16));
    }
    return bytes;
  }

  @Test
  public void current() {
    TransactionContext transactionContext = TransactionContext.current();
    assertThat(transactionContext)
        .extracting("spanId", "traceId")
        .containsOnly("0000000000000000", "00000000000000000000000000000000");
    assertThat(transactionContext.getContext())
        .as("Current Context")
        .isNotNull();
  }

  @Test
  public void ofValidSpan() {

    Span span = mock(Span.class);
    SpanContext spanContext = mock(SpanContext.class);
    when(spanContext.getSpanId()).thenReturn("53f9aa133a283c1a");
    when(spanContext.getSpanIdBytes()).thenReturn(hexToBytes("53f9aa133a283c1a"));
    when(spanContext.getTraceId()).thenReturn("fbc14552c62fbabc6a4bc6817cd983ce");
    when(span.getSpanContext()).thenReturn(spanContext);
    when(span.storeInContext(any(Context.class))).thenReturn(Context.current());
    Transaction transaction = mock(Transaction.class);
    String transactionId = "3eea0368-f4f9-4b31-88b3-fd08365fa1ba";
    when(transaction.getTransactionId()).thenReturn(transactionId);
    when(transaction.getTransactionSpan()).thenReturn(span);
    when(transaction.getSpan()).thenReturn(span);

    TransactionContext transactionContext = TransactionContext.of(span, transaction);
    assertThat(transactionContext)
        .extracting("spanId", "traceId")
        .containsOnly("53f9aa133a283c1a", "fbc14552c62fbabc6a4bc6817cd983ce");
    assertThat(transactionContext.getContext())
        .as("Current Context")
        .isNotNull();
    assertThat(transactionContext.getTraceContextMap())
        .isNotNull()
        .containsEntry("TRACE_TRANSACTION_ID", transactionId)
        .containsEntry("traceId", "fbc14552c62fbabc6a4bc6817cd983ce")
        .containsEntry("spanId", "53f9aa133a283c1a");
  }

}