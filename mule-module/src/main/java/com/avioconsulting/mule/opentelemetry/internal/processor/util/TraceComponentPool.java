package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
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

  /**
   * This limit controls the maximum number of reusable TraceComponent instances
   * that can be maintained in the pool.
   * 
   * @default 1000
   */
  private static final String MULE_OTEL_POOLING_TRACECOMPONENT_MAXSIZE = "mule.otel.pooling.tracecomponent.maxsize";

  private static final int MAX_POOL_SIZE = PropertiesUtil.getInt(MULE_OTEL_POOLING_TRACECOMPONENT_MAXSIZE, 1000);

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
        LOGGER.trace("Reused TraceComponent from pool (pool size: {}) - {} - {}", currentPoolSize.get(),
            pooled.getId() + " - " + pooled.getName(), pooled);
      }
      return pooled.withBorrowedAt(System.currentTimeMillis());
    }

    // Create new if pool is empty
    componentsCreated.incrementAndGet();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Created new TraceComponent (total created: {})", componentsCreated.get());
    }
    TraceComponent traceComponent = new PooledTraceComponent(transactionId, name, onClose).withBorrowedAt(
        System.currentTimeMillis());
    return traceComponent;
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
    TraceComponent component = acquire(transactionId, name)
        .withLocation(location.getLocation())
        .withComponentLocation(location);
    return component;
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
      LOGGER.trace("Returning TraceComponent to pool (pool size: {}): {} - {}", currentPoolSize.get(),
          pooled.getId() + " - " + pooled.getName(), pooled);
    }
    // Clear the component and its tags for reuse
    pooled.clear();

    // Only return to pool if under limit
    if (currentPoolSize.get() < MAX_POOL_SIZE) {
      componentPool.offer(pooled);
      currentPoolSize.incrementAndGet();
      componentsReturned.incrementAndGet();

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Returned TraceComponent to pool (pool size: {}) - {}", currentPoolSize.get(), pooled);
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