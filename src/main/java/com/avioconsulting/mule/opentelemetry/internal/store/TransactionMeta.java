package com.avioconsulting.mule.opentelemetry.internal.store;

import io.opentelemetry.api.trace.Span;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Get Transaction metadata information.
 */
public interface TransactionMeta extends Serializable {
  String getTransactionId();

  String getRootFlowName();

  String getTraceId();

  Instant getStartTime();

  Instant getEndTime();

  Span getSpan();

  Map<String, String> getTags();
}
