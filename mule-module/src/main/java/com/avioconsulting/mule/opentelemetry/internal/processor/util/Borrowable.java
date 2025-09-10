package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;

public interface Borrowable {
  long getBorrowedAt();

  TraceComponent withBorrowedAt(long borrowedAt);
}
