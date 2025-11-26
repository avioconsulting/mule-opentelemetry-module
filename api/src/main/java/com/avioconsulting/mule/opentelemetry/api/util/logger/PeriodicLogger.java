package com.avioconsulting.mule.opentelemetry.api.util.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A production-grade utility for periodic logging that reduces log spam by
 * throttling
 * repeated log messages to appear at configurable intervals.
 *
 * Logs are written immediately when the throttle period has elapsed,
 * ensuring they appear in the correct chronological context.
 *
 * Thread-safe, optimized for high throughput, and prevents memory leaks through
 * automatic cleanup of stale entries.
 *
 * <p>
 * Usage example:
 * 
 * <pre>
 * PeriodicLogger periodicLogger = PeriodicLogger.builder()
 * 		.interval(30, TimeUnit.SECONDS)
 * 		.maxCacheSize(10000)
 * 		.build();
 *
 * // In your high-frequency code:
 * periodicLogger.error(logger, "Database connection failed");
 * periodicLogger.warn(logger, "Cache miss for key: {}", cacheKey);
 * </pre>
 *
 */
public class PeriodicLogger {

  private static final Logger INTERNAL_LOGGER = LoggerFactory.getLogger(PeriodicLogger.class);
  private static final int DEFAULT_MAX_CACHE_SIZE = 10000;
  private static final long DEFAULT_INTERVAL_MS = 60000; // 1 minute

  private final ConcurrentHashMap<LogKey, LogThrottle> throttles;
  private final long intervalMs;
  private final long cleanupIntervalMs;
  private final int maxCacheSize;
  private final AtomicLong lastCleanupTimeMs;
  private final AtomicInteger cacheSize;
  private final boolean includeSuppressionCount;

  private PeriodicLogger(Builder builder) {
    this.intervalMs = builder.intervalMs;
    this.cleanupIntervalMs = Math.max(intervalMs * 10, TimeUnit.MINUTES.toMillis(5));
    this.maxCacheSize = builder.maxCacheSize;
    this.includeSuppressionCount = builder.includeSuppressionCount;
    this.throttles = new ConcurrentHashMap<>(Math.min(1024, maxCacheSize));
    this.lastCleanupTimeMs = new AtomicLong(System.currentTimeMillis());
    this.cacheSize = new AtomicInteger(0);
  }

  /**
   * Creates a builder for constructing a PeriodicLogger with custom
   * configuration.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a PeriodicLogger with default settings (1-minute interval).
   *
   * @return a new PeriodicLogger instance with a default configuration
   */
  public static PeriodicLogger createDefault() {
    return new Builder().build();
  }

  /**
   * Builder class for creating PeriodicLogger instances with custom
   * configuration.
   */
  public static class Builder {
    private long intervalMs = DEFAULT_INTERVAL_MS;
    private int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
    private boolean includeSuppressionCount = true;

    /**
     * Sets the minimum time interval between identical log messages.
     *
     * @param interval
     *            the interval duration
     * @param unit
     *            the time unit
     * @return this builder
     */
    public Builder interval(long interval, TimeUnit unit) {
      if (interval <= 0) {
        throw new IllegalArgumentException("Interval must be positive");
      }
      this.intervalMs = unit.toMillis(interval);
      return this;
    }

    /**
     * Sets the maximum number of unique log keys to cache.
     * When exceeded, cleanup is triggered immediately.
     *
     * @param maxCacheSize
     *            the maximum cache size (must be positive)
     * @return this builder
     */
    public Builder maxCacheSize(int maxCacheSize) {
      if (maxCacheSize <= 0) {
        throw new IllegalArgumentException("Max cache size must be positive");
      }
      this.maxCacheSize = maxCacheSize;
      return this;
    }

    /**
     * Sets whether to include suppression count in log messages.
     *
     * @param include
     *            true to include suppression count, false otherwise
     * @return this builder
     */
    public Builder includeSuppressionCount(boolean include) {
      this.includeSuppressionCount = include;
      return this;
    }

    /**
     * Builds the PeriodicLogger instance.
     *
     * @return a new PeriodicLogger
     */
    public PeriodicLogger build() {
      return new PeriodicLogger(this);
    }
  }

  /**
   * Records an ERROR level log, logging it immediately if the throttle period has
   * elapsed.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message
   */
  public void error(Logger logger, String message) {
    log(logger, Level.ERROR, message);
  }

  /**
   * Records an ERROR level log with arguments.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message with placeholders
   * @param args
   *            the arguments to substitute
   */
  public void error(Logger logger, String message, Object... args) {
    log(logger, Level.ERROR, message, args);
  }

  /**
   * Records a WARN level log.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message
   */
  public void warn(Logger logger, String message) {
    log(logger, Level.WARN, message);
  }

  /**
   * Records a WARN level log with arguments.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message with placeholders
   * @param args
   *            the arguments to substitute
   */
  public void warn(Logger logger, String message, Object... args) {
    log(logger, Level.WARN, message, args);
  }

  /**
   * Records an INFO level log.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message
   */
  public void info(Logger logger, String message) {
    log(logger, Level.INFO, message);
  }

  /**
   * Records an INFO level log with arguments.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message with placeholders
   * @param args
   *            the arguments to substitute
   */
  public void info(Logger logger, String message, Object... args) {
    log(logger, Level.INFO, message, args);
  }

  /**
   * Records a DEBUG level log.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message
   */
  public void debug(Logger logger, String message) {
    log(logger, Level.DEBUG, message);
  }

  /**
   * Records a DEBUG level log with arguments.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param message
   *            the log message with placeholders
   * @param args
   *            the arguments to substitute
   */
  public void debug(Logger logger, String message, Object... args) {
    log(logger, Level.DEBUG, message, args);
  }

  /**
   * Records a log statement, logging it immediately if the throttle period has
   * elapsed.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param level
   *            the log level
   * @param message
   *            the log message
   */
  public void log(Logger logger, Level level, String message) {
    log(logger, level, message, (Object[]) null);
  }

  /**
   * Records a log statement with arguments, logging it immediately if the
   * throttle period has elapsed.
   *
   * @param logger
   *            the SLF4J logger to use
   * @param level
   *            the log level
   * @param message
   *            the log message with placeholders
   * @param args
   *            the arguments to substitute
   */
  public void log(Logger logger, Level level, String message, Object... args) {
    if (logger == null || level == null || message == null) {
      INTERNAL_LOGGER.warn("Ignoring log call with null parameters");
      return;
    }

    try {
      LogKey key = new LogKey(logger.getName(), level, message);

      // Check cache size before adding new entries
      if (cacheSize.get() >= maxCacheSize && !throttles.containsKey(key)) {
        INTERNAL_LOGGER.warn("PeriodicLogger cache size limit reached ({}), triggering cleanup", maxCacheSize);
        performCleanup(System.currentTimeMillis(), true);

        // If still over limit after cleanup, log directly without throttling
        if (cacheSize.get() >= maxCacheSize) {
          logMessage(logger, level, message, args);
          return;
        }
      }

      LogThrottle throttle = throttles.computeIfAbsent(key, k -> {
        cacheSize.incrementAndGet();
        return new LogThrottle();
      });

      throttle.tryLog(() -> logMessage(logger, level, message, args),
          logger, includeSuppressionCount, intervalMs);

      // Periodically clean up stale entries
      cleanupIfNeeded();

    } catch (Exception e) {
      // Fallback: log directly if throttling fails
      INTERNAL_LOGGER.error("Error in periodic logging, falling back to direct log in DEBUG level", e);
      try {
        logMessage(logger, Level.DEBUG, message, args);
      } catch (Exception fallbackEx) {
        INTERNAL_LOGGER.error("Failed to log message even in fallback", fallbackEx);
      }
    }
  }

  /**
   * Returns the current number of cached log keys.
   * Useful for monitoring and diagnostics.
   *
   * @return the current cache size
   */
  public int getCacheSize() {
    return cacheSize.get();
  }

  /**
   * Returns the configured maximum cache size.
   *
   * @return the maximum cache size
   */
  public int getMaxCacheSize() {
    return maxCacheSize;
  }

  /**
   * Manually triggers cleanup of stale entries.
   * Normally cleanup happens automatically, but this can be called
   * for explicit control (e.g., before shutdown or in tests).
   */
  public void forceCleanup() {
    performCleanup(System.currentTimeMillis(), true);
  }

  private void logMessage(Logger logger, Level level, String message, Object[] args) {
    switch (level) {
      case ERROR:
        if (args == null || args.length == 0) {
          logger.error(message);
        } else {
          logger.error(message, args);
        }
        break;
      case WARN:
        if (args == null || args.length == 0) {
          logger.warn(message);
        } else {
          logger.warn(message, args);
        }
        break;
      case INFO:
        if (args == null || args.length == 0) {
          logger.info(message);
        } else {
          logger.info(message, args);
        }
        break;
      case DEBUG:
        if (args == null || args.length == 0) {
          logger.debug(message);
        } else {
          logger.debug(message, args);
        }
        break;
      case TRACE:
        if (args == null || args.length == 0) {
          logger.trace(message);
        } else {
          logger.trace(message, args);
        }
        break;
      default:
        INTERNAL_LOGGER.warn("Unsupported log level: {}", level);
    }
  }

  private void cleanupIfNeeded() {
    long now = System.currentTimeMillis();
    long lastCleanup = lastCleanupTimeMs.get();

    if (now - lastCleanup > cleanupIntervalMs) {
      // Try to acquire cleanup lock using proper CAS
      if (lastCleanupTimeMs.compareAndSet(lastCleanup, now)) {
        performCleanup(now, false);
      }
    }
  }

  private void performCleanup(long now, boolean force) {
    try {
      // Remove entries that haven't been accessed in 2x the cleanup interval
      long staleThreshold = now - (cleanupIntervalMs * 2);

      int removedCount = 0;
      for (java.util.Iterator<java.util.Map.Entry<LogKey, LogThrottle>> iterator = throttles.entrySet()
          .iterator(); iterator.hasNext();) {
        java.util.Map.Entry<LogKey, LogThrottle> entry = iterator.next();
        LogThrottle throttle = entry.getValue();
        long lastAccess = throttle.getLastAccessTime();

        if (force || lastAccess < staleThreshold) {
          iterator.remove();
          cacheSize.decrementAndGet();
          removedCount++;
        }
      }

      if (removedCount > 0) {
        INTERNAL_LOGGER.debug("PeriodicLogger cleanup removed {} stale entries, cache size now: {}",
            removedCount, cacheSize.get());
      }

    } catch (Exception e) {
      INTERNAL_LOGGER.error("Error during PeriodicLogger cleanup", e);
    }
  }

  /**
   * Internal throttle mechanism that tracks when a log was last written
   * and suppresses later attempts until the interval has elapsed.
   */
  private static class LogThrottle {
    private final AtomicLong lastLogTimeMs = new AtomicLong(0);
    private final AtomicLong suppressedCount = new AtomicLong(0);
    private final AtomicLong lastAccessTimeMs = new AtomicLong(System.currentTimeMillis());

    void tryLog(Runnable logAction, Logger logger, boolean includeSuppressionCount, long intervalMs) {
      long now = System.currentTimeMillis();
      lastAccessTimeMs.set(now); // Track access for cleanup

      long lastLog = lastLogTimeMs.get();

      // Check if enough time has passed
      if (now - lastLog >= intervalMs) {
        // Try to acquire the right to log using CAS
        if (lastLogTimeMs.compareAndSet(lastLog, now)) {
          // Get and reset the suppressed count
          long suppressed = suppressedCount.getAndSet(0);

          try {
            // Execute the actual log statement
            logAction.run();

            // If messages were suppressed and user wants to see the count
            if (suppressed > 0 && includeSuppressionCount) {
              logger.info("  └─ {} similar messages suppressed in last {} seconds",
                  suppressed, intervalMs / 1000);
            }
          } catch (Exception e) {
            INTERNAL_LOGGER.error("Error executing log action", e);
          }
        } else {
          // Another thread won the race, count this as suppressed
          suppressedCount.incrementAndGet();
        }
      } else {
        // Too soon, suppress this log
        suppressedCount.incrementAndGet();
      }
    }

    long getLastAccessTime() {
      return lastAccessTimeMs.get();
    }
  }

  /**
   * Internal key class for uniquely identifying log statements.
   * Immutable and optimized for use as a HashMap key.
   */
  private static final class LogKey {
    private final String loggerName;
    private final Level level;
    private final String message;
    private final int hashCode;

    LogKey(String loggerName, Level level, String message) {
      this.loggerName = loggerName;
      this.level = level;
      this.message = message;

      // Pre-compute hash code for performance
      int hash = 17;
      hash = 31 * hash + (loggerName != null ? loggerName.hashCode() : 0);
      hash = 31 * hash + (level != null ? level.hashCode() : 0);
      hash = 31 * hash + (message != null ? message.hashCode() : 0);
      this.hashCode = hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof LogKey))
        return false;

      LogKey other = (LogKey) o;

      if (loggerName == null ? other.loggerName != null : !loggerName.equals(other.loggerName)) {
        return false;
      }
      if (level != other.level) {
        return false;
      }
      return message == null ? other.message == null : message.equals(other.message);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return "LogKey{" +
          "logger='" + loggerName + '\'' +
          ", level=" + level +
          ", message='" + message + '\'' +
          '}';
    }
  }
}