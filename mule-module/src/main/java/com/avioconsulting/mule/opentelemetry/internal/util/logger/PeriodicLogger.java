package com.avioconsulting.mule.opentelemetry.internal.util.logger;

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

  /**
   * Maximum number of unique log messages to cache for throttling.
   *
   * @default 10000
   */
  public static final String MULE_OTEL_PERIODIC_LOGGER_CACHE_SIZE = "mule.otel.periodic.logger.cache.size";

  /**
   * The minimum time interval (in seconds) between identical log messages.
   *
   * @default 60
   */
  public static final String MULE_OTEL_PERIODIC_LOGGER_INTERVAL_SECONDS = "mule.otel.periodic.logger.interval.seconds";

  /**
   * Multiplier for the throttle interval to determine how often to clean up stale
   * entries.
   * For example, if interval is 60s and multiplier is 3, cleanup runs every 180s.
   *
   * @default 3
   */
  public static final String MULE_OTEL_PERIODIC_LOGGER_CLEANUP_MULTIPLIER = "mule.otel.periodic.logger.cleanup.multiplier";

  private static final int DEFAULT_MAX_CACHE_SIZE = Integer
      .getInteger(MULE_OTEL_PERIODIC_LOGGER_CACHE_SIZE, 10000);
  private static final long DEFAULT_INTERVAL_MS = Long
      .getLong(MULE_OTEL_PERIODIC_LOGGER_INTERVAL_SECONDS, 60) * 1000;
  private static final long CLEANUP_MULTIPLIER = Long
      .getLong(MULE_OTEL_PERIODIC_LOGGER_CLEANUP_MULTIPLIER, 3);

  private final ConcurrentHashMap<LogKey, LogThrottle> throttles;
  private final long intervalMs;
  private final long cleanupIntervalMs;
  private final int maxCacheSize;
  private final AtomicLong lastCleanupTimeMs;
  private final AtomicInteger cacheSize;
  private final boolean includeSuppressionCount;
  private final AtomicInteger newEntriesLastMinute = new AtomicInteger(0);
  private final AtomicLong lastRateCheckMs = new AtomicLong(System.currentTimeMillis());

  private PeriodicLogger(Builder builder) {
    this.intervalMs = builder.intervalMs;
    this.cleanupIntervalMs = Math.max(intervalMs * CLEANUP_MULTIPLIER, TimeUnit.MINUTES.toMillis(2));
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

  public void error(Logger logger, String message) {
    log(logger, Level.ERROR, message);
  }

  public void error(Logger logger, String message, Object... args) {
    log(logger, Level.ERROR, message, args);
  }

  public void warn(Logger logger, String message) {
    log(logger, Level.WARN, message);
  }

  public void warn(Logger logger, String message, Object... args) {
    log(logger, Level.WARN, message, args);
  }

  public void info(Logger logger, String message) {
    log(logger, Level.INFO, message);
  }

  public void info(Logger logger, String message, Object... args) {
    log(logger, Level.INFO, message, args);
  }

  public void debug(Logger logger, String message) {
    log(logger, Level.DEBUG, message);
  }

  public void debug(Logger logger, String message, Object... args) {
    log(logger, Level.DEBUG, message, args);
  }

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

    // Early exit if level is not enabled - hot path optimization
    if (!isLevelEnabled(logger, level)) {
      return;
    }

    long now = System.currentTimeMillis();

    try {
      LogKey key = new LogKey(logger.getName(), level, message);

      // Optimistic read first (lock-free fast path)
      LogThrottle throttle = throttles.get(key);

      if (throttle == null) {
        if (cacheSize.get() >= maxCacheSize) {
          INTERNAL_LOGGER.warn("PeriodicLogger cache size limit reached ({}), triggering cleanup",
              maxCacheSize);
          performCleanup(now, true);

          // If still over limit after cleanup, log directly without throttling
          if (cacheSize.get() >= maxCacheSize) {
            logMessage(logger, level, message, args);
            return;
          }
        }

        LogThrottle newThrottle = new LogThrottle(now);
        LogThrottle existing = throttles.putIfAbsent(key, newThrottle);
        if (existing != null) {
          throttle = existing;
        } else {
          throttle = newThrottle;
          cacheSize.incrementAndGet();
          detectHighCardinality(now);
        }
      }

      throttle.tryLog(() -> logMessage(logger, level, message, args),
          logger, level, includeSuppressionCount, intervalMs, now);

      // Periodically clean up stale entries
      cleanupIfNeeded(now);

    } catch (Exception e) {
      // Fallback: log directly if throttling fails. Keep original level.
      INTERNAL_LOGGER.error("Error in periodic logging, falling back to direct log", e);
      try {
        logMessage(logger, level, message, args);
      } catch (Exception fallbackEx) {
        INTERNAL_LOGGER.error("Failed to log message even in fallback", fallbackEx);
      }
    }
  }

  private boolean isLevelEnabled(Logger logger, Level level) {
    switch (level) {
      case ERROR:
        return logger.isErrorEnabled();
      case WARN:
        return logger.isWarnEnabled();
      case INFO:
        return logger.isInfoEnabled();
      case DEBUG:
        return logger.isDebugEnabled();
      case TRACE:
        return logger.isTraceEnabled();
      default:
        return false;
    }
  }

  private void detectHighCardinality(long now) {
    long lastCheck = lastRateCheckMs.get();
    if (now - lastCheck > 60000) {
      if (lastRateCheckMs.compareAndSet(lastCheck, now)) {
        newEntriesLastMinute.set(0);
      }
    }
    int newCount = newEntriesLastMinute.incrementAndGet();
    if (newCount > 1000) {
      INTERNAL_LOGGER.warn(
          "PeriodicLogger: High rate of unique log messages detected ({}/min). " +
              "This may indicate string concatenation instead of parameterized logging. " +
              "Example: use logger.error(\"Failed for {}\", id) instead of logger.error(\"Failed for \" + id)",
          newCount);
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

  private void cleanupIfNeeded(long now) {
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
    private final AtomicLong lastAccessTimeMs;

    LogThrottle(long now) {
      this.lastAccessTimeMs = new AtomicLong(now);
    }

    void tryLog(Runnable logAction, Logger logger, Level level, boolean includeSuppressionCount, long intervalMs,
        long now) {
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
              logSuppressionCount(logger, level, suppressed, intervalMs);
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

    private void logSuppressionCount(Logger logger, Level level, long suppressed, long intervalMs) {
      String msg = "  * {} similar messages suppressed in last {} seconds";
      long intervalSecs = intervalMs / 1000;
      switch (level) {
        case ERROR:
          logger.error(msg, suppressed, intervalSecs);
          break;
        case WARN:
          logger.warn(msg, suppressed, intervalSecs);
          break;
        case INFO:
          logger.info(msg, suppressed, intervalSecs);
          break;
        case DEBUG:
          logger.debug(msg, suppressed, intervalSecs);
          break;
        case TRACE:
          logger.trace(msg, suppressed, intervalSecs);
          break;
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
    private volatile int hashCode = 0;

    LogKey(String loggerName, Level level, String message) {
      this.loggerName = loggerName;
      this.level = level;
      this.message = message;
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
      int h = hashCode;
      if (h == 0) {
        h = 17;
        h = 31 * h + (loggerName != null ? loggerName.hashCode() : 0);
        h = 31 * h + (level != null ? level.hashCode() : 0);
        h = 31 * h + (message != null ? message.hashCode() : 0);
        hashCode = h;
      }
      return h;
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
