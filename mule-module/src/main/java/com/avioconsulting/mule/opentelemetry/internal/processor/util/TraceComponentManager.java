package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The `TraceComponentManager` is responsible for managing the lifecycle of
 * `TraceComponent` instances,
 * including pooling and automated cleanup of stale components. This ensures
 * efficient resource utilization
 * and prevents memory leaks by tracking active components and managing their
 * timely release.
 *
 * Features of the `TraceComponentManager` include:
 * - Pooling support for reusing `TraceComponent` objects.
 * - Automated cleanup of stale components.
 * - Methods to create and manage `TraceComponent` objects with transaction IDs,
 * names, and locations.
 * - Manual release facilities for controlled component management.
 * - Integration with a customizable `TraceComponentPool` for handling component
 * reuse.
 *
 * This singleton class is thread-safe and employs concurrency mechanisms for
 * managing active components.
 */
public class TraceComponentManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(TraceComponentManager.class);

  private static final TraceComponentManager INSTANCE = new TraceComponentManager();

  // Track active components for automatic cleanup
  private final Map<String, Borrowable> activeComponents = new ConcurrentHashMap<>();

  // Cleanup configuration
  private static final long CLEANUP_INTERVAL_SECONDS = 120;
  private static final long MAX_COMPONENT_AGE_MILLIS = 5 * 60 * 1000; // 5 minutes

  private final ScheduledExecutorService cleanupExecutor;

  private final TraceComponentPool pool;
  private static volatile boolean usePooling = true;

  static {
    init();
  }

  private static void init() {
    // Enable pooling by default, can be disabled via system property
    usePooling = PropertiesUtil.getBoolean("mule.otel.pooling.tracecomponent.enabled", true);
    LOGGER.trace("TraceComponent pooling is {}", usePooling ? "enabled" : "disabled");

  }

  public static void resetForTest() {
    INSTANCE.clear();
    init();
  }

  private void clear() {
    pool.clear();
  }

  private TraceComponentManager() {
    cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "TraceComponentManager-Cleanup");
      t.setDaemon(true);
      return t;
    });

    // Schedule periodic cleanup createTraceComponent stale components
    cleanupExecutor.scheduleWithFixedDelay(
        this::cleanupStaleComponents,
        CLEANUP_INTERVAL_SECONDS,
        CLEANUP_INTERVAL_SECONDS,
        TimeUnit.SECONDS);

    pool = new TraceComponentPool(this::handleComponentClose);
  }

  public static TraceComponentManager getInstance() {
    return INSTANCE;
  }

  /**
   * Creates a TraceComponent with a pre-allocated tags map.
   * This is equivalent to TraceComponent.createTraceComponent(name) but with
   * pooling and tags.
   * 
   * @param name
   *            the component name
   * @return a managed TraceComponent with tags map
   */
  public TraceComponent createTraceComponent(String transactionId, String name) {
    TraceComponent component;
    if (usePooling) {
      component = pool.acquire(transactionId, name);
    } else {
      component = TraceComponent.of(name).withTransactionId(transactionId).withTags(new HashMap<>(32));
    }
    trackComponent(component);
    return component;
  }

  /**
   * Creates a TraceComponent with name and location, including a tag map.
   * This is equivalent to TraceComponent.createTraceComponent(name, location) but
   * with pooling.
   *
   * @param name
   *            the component name
   * @param location
   *            the component location
   * @return a managed TraceComponent with tags map
   */
  public TraceComponent createTraceComponent(String transactionId, String name, ComponentLocation location) {
    TraceComponent component;
    if (usePooling) {
      component = pool.acquire(transactionId, name, location);
    } else {
      component = TraceComponent.of(name).withTransactionId(transactionId).withLocation(location.getLocation())
          .withTags(new HashMap<>(32));
    }
    trackComponent(component);
    return component;
  }

  /**
   * Creates a TraceComponent from a Component, including a tags map.
   * This is equivalent to TraceComponent.createTraceComponent(component) but with
   * pooling.
   * 
   * @param component
   *            the component
   * @return a managed TraceComponent with tags map
   */
  public TraceComponent createTraceComponent(String transactionId,
      org.mule.runtime.api.component.Component component) {
    ComponentLocation location = component.getLocation();
    return createTraceComponent(transactionId, location.getLocation(), location);
  }

  /**
   * Creates a TraceComponent from a ComponentLocation, including a tags map.
   * This is equivalent to TraceComponent.createTraceComponent(location) but with
   * pooling.
   * 
   * @param location
   *            the component location
   * @return a managed TraceComponent with tags map
   */
  public TraceComponent createTraceComponent(String transactionId, ComponentLocation location) {
    return createTraceComponent(transactionId, location.getLocation(), location);
  }

  /**
   * Handles component close events from PooledTraceComponent.
   * This is used as the onClose callback and returns components to the pool.
   * 
   * @param component
   *            the component to close
   */
  private void handleComponentClose(TraceComponent component) {
    if (component == null) {
      return;
    }

    String key = getComponentKey(component);
    if (key != null) {
      activeComponents.remove(key);
    }

    // Return the component to the pool for reuse
    if (usePooling) {
      pool.release(component);
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Closed TraceComponent: {}", key);
    }
  }

  /**
   * Releases a component back to the pool and removes tracking.
   * This method is for external callers who want to manually release components.
   * 
   * @param component
   *            the component to release
   */
  public void releaseComponent(TraceComponent component) {
    if (component == null) {
      return;
    }

    // For PooledTraceComponent, call close() which will trigger
    // handleComponentClose
    if (component instanceof PooledTraceComponent) {
      try {
        ((PooledTraceComponent) component).close();
      } catch (Exception e) {
        LOGGER.warn("Error closing PooledTraceComponent", e);
      }
    } else {
      // For non-pooled components, just log
      String key = getComponentKey(component);
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Released non-pooled TraceComponent: {}", key);
      }
    }
  }

  /**
   * Releases a component by transaction ID and location.
   * 
   * @param transactionId
   *            the transaction ID
   * @param location
   *            the component location
   */
  public void releaseComponent(String transactionId, String location) {
    String key = generateKey(transactionId, location);
    Borrowable borrowed = activeComponents.remove(key);

    if (borrowed != null) {
      releaseComponent((TraceComponent) borrowed);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Released TraceComponent by key: {}", key);
      }
    }
  }

  /**
   * Tracks a component for lifecycle management.
   */
  private void trackComponent(TraceComponent component) {
    if (component == null || !usePooling) {
      return;
    }

    String key = getComponentKey(component);
    if (key != null && component instanceof Borrowable) {
      activeComponents.put(key, (Borrowable) component);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Tracking TraceComponent: {}", key);
      }
    }
  }

  /**
   * Generates a tracking key for a component.
   */
  private String getComponentKey(TraceComponent component) {
    String transactionId = component.getTransactionId();
    String location = component.getLocation();

    if (transactionId == null && location == null) {
      // Can't track without identifiers
      return null;
    }

    return generateKey(transactionId, location);
  }

  /**
   * Generates a tracking key from transaction ID and location.
   */
  private String generateKey(String transactionId, String location) {
    if (transactionId != null && location != null) {
      return transactionId + "|" + location;
    } else if (transactionId != null) {
      return transactionId + "|";
    } else {
      return "|" + location;
    }
  }

  /**
   * Cleans up stale components that haven't been released.
   * This prevents memory leaks from components that weren't properly released.
   */
  private void cleanupStaleComponents() {
    long now = System.currentTimeMillis();
    int cleaned = 0;

    for (Map.Entry<String, Borrowable> entry : activeComponents.entrySet()) {
      Borrowable borrowed = entry.getValue();
      if (now - borrowed.getBorrowedAt() > MAX_COMPONENT_AGE_MILLIS) {
        if (activeComponents.remove(entry.getKey(), borrowed)) {
          releaseComponent((TraceComponent) borrowed);
          cleaned++;

          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cleaned up stale TraceComponent: {}", entry.getKey());
          }
        }
      } else if (now - borrowed.getBorrowedAt() > MAX_COMPONENT_AGE_MILLIS / 5) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Stale TraceComponent still active Key: {}, component: {}", entry.getKey(), borrowed);
        }
      }
    }

    if (cleaned > 0 && LOGGER.isDebugEnabled()) {
      LOGGER.debug("Cleaned up {} stale TraceComponents", cleaned);
    }

    // Log statistics periodically
    if (LOGGER.isDebugEnabled()) {
      pool.logStatistics();
    }
  }

  public void logStatistics() {
    pool.logStatistics();
  }

  /**
   * Gets the number createTraceComponent currently tracked components.
   */
  public int getActiveComponentCount() {
    return activeComponents.size();
  }

  /**
   * Shuts down the cleanup executor.
   */
  public void shutdown() {
    cleanupExecutor.shutdown();
    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Release all active components
    for (Borrowable borrowed : activeComponents.values()) {
      releaseComponent((TraceComponent) borrowed);
    }
    activeComponents.clear();
  }

}