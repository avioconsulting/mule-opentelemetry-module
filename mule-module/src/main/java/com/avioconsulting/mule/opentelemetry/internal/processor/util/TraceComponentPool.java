package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Object pool for TraceComponent instances to reduce GC pressure.
 * This pool manages TraceComponent instances, significantly reducing object
 * allocation.
 */
public class TraceComponentPool {

  private static final Logger LOGGER = LoggerFactory.getLogger(TraceComponentPool.class);

  // Pool configuration
  private static final int MAX_POOL_SIZE = 200;

  // Pools
  private final Queue<PooledTraceComponent> componentPool = new ConcurrentLinkedQueue<>();

  // Statistics
  private final AtomicLong componentsCreated = new AtomicLong(0);
  private final AtomicLong componentsReused = new AtomicLong(0);
  private final AtomicLong componentsReturned = new AtomicLong(0);
  private final AtomicInteger currentPoolSize = new AtomicInteger(0);
  private final Consumer<TraceComponent> onClose;

  TraceComponentPool(Consumer<TraceComponent> onClose) {
    this.onClose = onClose;
    prewarmPool();
  }

  /**
   * Acquires a TraceComponent from the pool or creates a new one.
   * 
   * @param name
   *            the component name
   * @return a TraceComponent instance
   */
  public TraceComponent acquire(String transactionId, String name) {
    PooledTraceComponent pooled = componentPool.poll();
    if (pooled != null) {
      currentPoolSize.decrementAndGet();
      componentsReused.incrementAndGet();
      pooled.reset(transactionId, name);
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Reused TraceComponent from pool (pool size: {}) - {}", currentPoolSize.get(),
            pooled.getId() + " - " + pooled.getName());
      }
      return pooled.withBorrowedAt(System.currentTimeMillis());
    }

    // Create new if pool is empty
    componentsCreated.incrementAndGet();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Created new TraceComponent (total created: {})", componentsCreated.get());
    }
    return new PooledTraceComponent(transactionId, name, onClose).withBorrowedAt(System.currentTimeMillis());
  }

  /**
   * Acquires a TraceComponent from the pool with name and location.
   * 
   * @param name
   *            the component name
   * @param location
   *            the component location
   * @return a TraceComponent instance
   */
  public TraceComponent acquire(String transactionId, String name, ComponentLocation location) {
    TraceComponent component = acquire(transactionId, name);
    return component
        .withLocation(location.getLocation())
        .withComponentLocation(location);
  }

  /**
   * Returns a TraceComponent to the pool for reuse.
   * 
   * @param component
   *            the component to return
   */
  public void release(TraceComponent component) {
    if (!(component instanceof PooledTraceComponent)) {
      return;
    }

    PooledTraceComponent pooled = (PooledTraceComponent) component;
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Returning TraceComponent to pool (pool size: {}): {}", currentPoolSize.get(),
          pooled.getId() + " - " + pooled.getName());
    }
    // Clear the component and its tags for reuse
    pooled.clear();

    // Only return to pool if under limit
    if (currentPoolSize.get() < MAX_POOL_SIZE) {
      componentPool.offer(pooled);
      currentPoolSize.incrementAndGet();
      componentsReturned.incrementAndGet();

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Returned TraceComponent to pool (pool size: {})", currentPoolSize.get());
      }
    }
  }

  /**
   * Pre-warms the pool with initial instances.
   */
  private void prewarmPool() {
    int prewarmSize = Math.min(50, MAX_POOL_SIZE);
    for (int i = 0; i < prewarmSize; i++) {
      PooledTraceComponent component = new PooledTraceComponent(null, null, onClose);
      componentPool.offer(component);
      currentPoolSize.incrementAndGet();
    }

    LOGGER.trace("Pre-warmed pool: {} components", prewarmSize);
  }

  /**
   * Clears the pool, useful for testing or shutdown.
   */
  public void clear() {
    componentPool.clear();
    currentPoolSize.set(0);
  }

  /**
   * Gets statistics about pool usage.
   *
   */
  public void logStatistics() {
    long totalComponentOps = componentsCreated.get() + componentsReused.get();
    double componentReuseRate = totalComponentOps > 0 ? (100.0 * componentsReused.get()) / totalComponentOps : 0;
    LOGGER.trace("TraceComponent Pool Stats: created={}, reused={}, returned={}, pool_size={}, reuse_rate={}",
        componentsCreated.get(),
        componentsReused.get(),
        componentsReturned.get(),
        currentPoolSize.get(),
        componentReuseRate);
  }

}