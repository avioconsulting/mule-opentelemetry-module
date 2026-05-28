package com.avioconsulting.mule.opentelemetry.internal.processor.util;

/**
 * Internal lifecycle contract for pooled components tracked by lease.
 *
 * <p>
 * This interface was introduced as an incremental step away from the older
 * {@code Borrowable} model toward explicit lease semantics. Pool and manager
 * logic requires two distinct signals:
 *
 * <ul>
 * <li><b>Lease generation</b> via {@link #getActiveLease()} to detect stale
 * close/release attempts when a component has already been re-acquired for a
 * newer lifecycle.</li>
 * <li><b>Lease start time</b> via {@link #getLeasedAt()} to compute age-based
 * cleanup thresholds for components that were not properly closed.</li>
 * </ul>
 *
 * <p>
 * The combination of immutable component identity ({@link #getId()}) and
 * lease metadata allows {@code TraceComponentManager} to manage concurrency
 * safely while preserving time-based stale cleanup behavior.
 */
public interface Leasable {

  String getId();

  long getActiveLease();

  long getLeasedAt();
}
