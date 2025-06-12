package com.avioconsulting.mule.opentelemetry.api.store;

import io.opentelemetry.context.Context;

/**
 * Get Span metadata information. Also see {@link TransactionMeta}.
 */
public interface SpanMeta extends TransactionMeta {
  String getSpanId();

  Context getContext();
}
