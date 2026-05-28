package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A subclass of {@code TraceComponent} that supports pooling by enabling the
 * reuse of its instances.
 * This class ensures efficient management of resources by providing methods to
 * reset and clear its state.
 *
 * Instances of {@code PooledTraceComponent} are identified by a unique
 * identifier and support custom
 * cleanup actions upon closure, as specified by the {@code onClose} consumer.
 */
public class PooledTraceComponent extends TraceComponent implements Leasable {
  private final Consumer<TraceComponent> onClose;

  private static final int INITIAL_TAG_MAP_CAPACITY = 64;

  private final String id = UUID.randomUUID().toString();
  private volatile long leasedAt;
  private final AtomicLong leaseCounter = new AtomicLong(0);
  private final AtomicLong lastClosedLease = new AtomicLong(-1);
  private volatile long activeLease;
  private volatile long ownerThreadId;

  PooledTraceComponent(String transactionId, String name, Consumer<TraceComponent> onClose) {
    super(name, new HashMap<>(INITIAL_TAG_MAP_CAPACITY));
    this.withTransactionId(transactionId);
    this.onClose = Objects.requireNonNull(onClose);
  }

  public String getId() {
    return id;
  }

  /**
   * Resets the component for reuse with a new transaction id and name.
   * State is already cleared by
   * {@link TraceComponentPool#release(TraceComponent)}
   * before the component is returned to the pool, so no explicit clear is needed
   * here.
   */
  void reset(String transactionId, String name) {
    this.setName(name)
        .withTransactionId(transactionId)
        .withStartTime(Instant.now());
  }

  long nextLease() {
    this.activeLease = leaseCounter.incrementAndGet();
    this.ownerThreadId = Thread.currentThread().getId();
    this.leasedAt = System.currentTimeMillis();
    return this.activeLease;
  }

  @Override
  public long getActiveLease() {
    return activeLease;
  }

  private boolean tryCloseCurrentLease() {
    long currentLease = this.activeLease;
    if (Thread.currentThread().getId() != ownerThreadId) {
      return false;
    }
    return lastClosedLease.getAndSet(currentLease) != currentLease;
  }

  @Override
  public void close() {
    if (!tryCloseCurrentLease()) {
      return;
    }
    onClose.accept(this);
  }

  @Override
  public long getLeasedAt() {
    return this.leasedAt;
  }

  @Override
  public String toString() {
    return "PooledTraceComponent{" +
        "id='" + id + '\'' +
        ", activeLease=" + activeLease +
        ", leasedAt=" + leasedAt +
        "} " + super.toString();
  }
}
