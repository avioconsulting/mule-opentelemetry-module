package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;

/**
 *
 * This interface is marked as deprecated and may be removed in future
 * versions.
 *
 * @see Leasable
 */

@Deprecated
public interface Borrowable {

  long getBorrowedAt();

  TraceComponent withBorrowedAt(long borrowedAt);
}
