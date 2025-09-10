package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
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
public class PooledTraceComponent extends TraceComponent implements Borrowable {
  private final Consumer<TraceComponent> onClose;

  private static final int INITIAL_TAG_MAP_CAPACITY = 64;

  private final String id = UUID.randomUUID().toString();
  private long borrowedAt;

  PooledTraceComponent(String transactionId, String name, Consumer<TraceComponent> onClose) {
    super(name, new HashMap<>(INITIAL_TAG_MAP_CAPACITY));
    this.withTransactionId(transactionId);
    this.onClose = Objects.requireNonNull(onClose);
  }

  public String getId() {
    return id;
  }

  /**
   * Resets the component for reuse with a new name.
   */
  void reset(String transactionId, String name) {
    this.setName(name)
        .withTransactionId(transactionId)
        .withStartTime(Instant.now());
  }

  @Override
  public void close() {
    onClose.accept(this);
  }

  @Override
  public long getBorrowedAt() {
    return this.borrowedAt;
  }

  @Override
  public TraceComponent withBorrowedAt(long borrowedAt) {
    this.borrowedAt = borrowedAt;
    return this;
  }

}
