package com.avioconsulting.mule.opentelemetry.internal.util.logger;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PeriodicLoggerTest {

  @Test
  public void shouldThrottleRepeatedMessages() {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(1, TimeUnit.SECONDS)
        .build();

    // Log 100 times
    for (int i = 0; i < 100; i++) {
      periodicLogger.error(mockLogger, "Test error");
    }

    // Should only log once
    verify(mockLogger, times(1)).error("Test error");
  }

  @Test
  public void shouldLogAfterIntervalElapses() throws InterruptedException {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(50, TimeUnit.MILLISECONDS)
        .build();

    periodicLogger.error(mockLogger, "Test error");
    Thread.sleep(100);
    periodicLogger.error(mockLogger, "Test error");

    verify(mockLogger, times(2)).error("Test error");
  }

  @Test
  public void shouldLogSuppressionCount() throws InterruptedException {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(50, TimeUnit.MILLISECONDS)
        .includeSuppressionCount(true)
        .build();

    periodicLogger.error(mockLogger, "Test error");
    for (int i = 0; i < 9; i++) {
      periodicLogger.error(mockLogger, "Test error");
    }

    Thread.sleep(100);
    periodicLogger.error(mockLogger, "Test error");

    verify(mockLogger, times(2)).error("Test error");
    verify(mockLogger, times(1)).error(anyString(), any(), any());
  }

  @Test
  public void shouldHandleHighConcurrency() throws InterruptedException {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(1, TimeUnit.SECONDS)
        .build();

    int threads = 20;
    int logsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
      executor.submit(() -> {
        try {
          for (int j = 0; j < logsPerThread; j++) {
            periodicLogger.error(mockLogger, "Concurrent error");
          }
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    // Should log once or twice depending on timing, but definitely not 2000 times
    verify(mockLogger, atMost(5)).error("Concurrent error");
  }

  @Test
  public void shouldCleanupStaleEntries() throws InterruptedException {
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(10, TimeUnit.MILLISECONDS)
        .maxCacheSize(100)
        .build();

    Logger logger = LoggerFactory.getLogger("test");
    periodicLogger.error(logger, "Test 1");
    periodicLogger.error(logger, "Test 2");

    assertThat(periodicLogger.getCacheSize()).isEqualTo(2);

    // Wait for stale threshold (multiplier=3, interval=10ms -> cleanup=30ms,
    // stale=60ms)
    Thread.sleep(200);
    periodicLogger.forceCleanup();

    assertThat(periodicLogger.getCacheSize()).isEqualTo(0);
  }

  @Test
  public void shouldLimitCacheSize() {
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(1, TimeUnit.MINUTES)
        .maxCacheSize(10)
        .build();

    Logger logger = LoggerFactory.getLogger("test");
    for (int i = 0; i < 20; i++) {
      periodicLogger.error(logger, "Error " + i);
    }

    // It should trigger cleanup and keep it around the limit
    assertThat(periodicLogger.getCacheSize()).isLessThanOrEqualTo(10);
  }

  @Test
  public void shouldEarlyExitIfLevelDisabled() {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(false);
    PeriodicLogger periodicLogger = PeriodicLogger.builder().build();

    periodicLogger.error(mockLogger, "Test error");

    // Should not even check cache or throttle
    verify(mockLogger, never()).error(anyString());
    assertThat(periodicLogger.getCacheSize()).isEqualTo(0);
  }

  @Test
  public void shouldHandleNullParametersGracefully() {
    PeriodicLogger periodicLogger = PeriodicLogger.createDefault();
    periodicLogger.log(null, Level.ERROR, "msg");
    periodicLogger.log(mock(Logger.class), null, "msg");
    periodicLogger.log(mock(Logger.class), Level.ERROR, null);
    // Should not throw exception
    assertThat(periodicLogger.getCacheSize()).isEqualTo(0);
  }

  @Test
  public void shouldDifferentiateByMessageTemplate() {
    Logger mockLogger = mock(Logger.class);
    when(mockLogger.isErrorEnabled()).thenReturn(true);
    PeriodicLogger periodicLogger = PeriodicLogger.builder()
        .interval(1, TimeUnit.MINUTES)
        .build();

    periodicLogger.error(mockLogger, "Error at location A");
    periodicLogger.error(mockLogger, "Error at location B");

    verify(mockLogger).error("Error at location A");
    verify(mockLogger).error("Error at location B");
    assertThat(periodicLogger.getCacheSize()).isEqualTo(2);
  }
}
