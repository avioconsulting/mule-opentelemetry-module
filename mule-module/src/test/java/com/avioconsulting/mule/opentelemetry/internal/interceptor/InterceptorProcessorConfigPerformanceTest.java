package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Time-based performance test for shouldIntercept() method in
 * InterceptorProcessorConfig.
 * This test measures the execution time of the shouldIntercept() method under
 * various scenarios
 * to ensure performance characteristics remain within acceptable bounds.
 */
public class InterceptorProcessorConfigPerformanceTest extends AbstractInternalTest {

  private static final int WARMUP_ITERATIONS = 1000;
  private static final int PERFORMANCE_ITERATIONS = 10000;
  private static final long MAX_ACCEPTABLE_AVG_TIME_NANOS = TimeUnit.MICROSECONDS.toNanos(10); // 10 microseconds

  private @NotNull ComponentLocation getLocation(String namespace, String name) {
    return getLocationAt(namespace, name, "anything-but-0");
  }

  private @NotNull ComponentLocation getLocationAtZero(String namespace, String name) {
    return getLocationAt(namespace, name, "0");
  }

  private @NotNull ComponentLocation getLocationAt(String namespace, String name, String locationEndPath) {
    DefaultComponentLocation.DefaultLocationPart part = new DefaultComponentLocation.DefaultLocationPart(
        locationEndPath,
        Optional.of(getComponentIdentifier(namespace, name, TypedComponentIdentifier.ComponentType.UNKNOWN)),
        Optional.empty(),
        OptionalInt.empty(),
        OptionalInt.empty());

    return new DefaultComponentLocation(Optional.of(name), Arrays.asList(rootFlowPart, processorsPart, part));
  }

  @Test
  public void shouldIntercept_performance_test_with_enabled_interceptors() {
    System.out.println("[DEBUG_LOG] Starting performance test for shouldIntercept() with enabled interceptors");

    // Setup test data
    ComponentLocation location = getLocation("http", "request");
    Event event = getEvent();

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(new MuleComponent("http", "request")));

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);
    interceptorProcessorConfig.setComponentRegistryService(mock(ComponentRegistryService.class));

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      interceptorProcessorConfig.shouldIntercept(location, event);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      boolean result = interceptorProcessorConfig.shouldIntercept(location, event);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isTrue();
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for enabled interceptors:");
    System.out.println("[DEBUG_LOG] Total iterations: " + PERFORMANCE_ITERATIONS);
    System.out.println("[DEBUG_LOG] Total time: " + totalTime + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMillis(totalTime) + " ms)");
    System.out.println("[DEBUG_LOG] Average time per call: " + avgTimePerCall + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMicros(avgTimePerCall) + " microseconds)");
    System.out.println(
        "[DEBUG_LOG] Throughput: " + (PERFORMANCE_ITERATIONS * 1_000_000_000L / totalTime) + " calls/second");

    // Performance assertion
    assertThat(avgTimePerCall)
        .as("Average execution time should be less than %d nanoseconds (%d microseconds)",
            MAX_ACCEPTABLE_AVG_TIME_NANOS, TimeUnit.NANOSECONDS.toMicros(MAX_ACCEPTABLE_AVG_TIME_NANOS))
        .isLessThan(MAX_ACCEPTABLE_AVG_TIME_NANOS);
  }

  @Test
  public void shouldIntercept_performance_test_with_disabled_interceptors() {
    System.out.println("[DEBUG_LOG] Starting performance test for shouldIntercept() with disabled interceptors");

    // Setup test data
    ComponentLocation location = getLocation("http", "request");
    Event event = getEvent();

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.singletonList(new MuleComponent("http", "request")), Collections.emptyList());

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);
    interceptorProcessorConfig.setComponentRegistryService(mock(ComponentRegistryService.class));

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      interceptorProcessorConfig.shouldIntercept(location, event);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      boolean result = interceptorProcessorConfig.shouldIntercept(location, event);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isFalse();
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for disabled interceptors:");
    System.out.println("[DEBUG_LOG] Total iterations: " + PERFORMANCE_ITERATIONS);
    System.out.println("[DEBUG_LOG] Total time: " + totalTime + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMillis(totalTime) + " ms)");
    System.out.println("[DEBUG_LOG] Average time per call: " + avgTimePerCall + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMicros(avgTimePerCall) + " microseconds)");
    System.out.println(
        "[DEBUG_LOG] Throughput: " + (PERFORMANCE_ITERATIONS * 1_000_000_000L / totalTime) + " calls/second");

    // Performance assertion
    assertThat(avgTimePerCall)
        .as("Average execution time should be less than %d nanoseconds (%d microseconds)",
            MAX_ACCEPTABLE_AVG_TIME_NANOS, TimeUnit.NANOSECONDS.toMicros(MAX_ACCEPTABLE_AVG_TIME_NANOS))
        .isLessThan(MAX_ACCEPTABLE_AVG_TIME_NANOS);
  }

  @Test
  public void shouldIntercept_performance_test_first_processor_scenario() {
    System.out.println("[DEBUG_LOG] Starting performance test for shouldIntercept() with first processor scenario");

    // Setup test data - first processor in flow
    ComponentLocation location = getLocationAtZero("http", "request");
    Event event = getEvent();

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.setComponentRegistryService(mock(ComponentRegistryService.class));

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      interceptorProcessorConfig.shouldIntercept(location, event);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      boolean result = interceptorProcessorConfig.shouldIntercept(location, event);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isTrue();
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for first processor scenario:");
    System.out.println("[DEBUG_LOG] Total iterations: " + PERFORMANCE_ITERATIONS);
    System.out.println("[DEBUG_LOG] Total time: " + totalTime + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMillis(totalTime) + " ms)");
    System.out.println("[DEBUG_LOG] Average time per call: " + avgTimePerCall + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMicros(avgTimePerCall) + " microseconds)");
    System.out.println(
        "[DEBUG_LOG] Throughput: " + (PERFORMANCE_ITERATIONS * 1_000_000_000L / totalTime) + " calls/second");

    // Performance assertion
    assertThat(avgTimePerCall)
        .as("Average execution time should be less than %d nanoseconds (%d microseconds)",
            MAX_ACCEPTABLE_AVG_TIME_NANOS, TimeUnit.NANOSECONDS.toMicros(MAX_ACCEPTABLE_AVG_TIME_NANOS))
        .isLessThan(MAX_ACCEPTABLE_AVG_TIME_NANOS);
  }

  @Test
  public void shouldIntercept_performance_test_with_tracing_disabled() {
    System.out
        .println("[DEBUG_LOG] Starting performance test for shouldIntercept() with tracing disabled globally");

    // Setup test data
    ComponentLocation location = getLocation("http", "request");
    Event event = getEvent();

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.setTurnOffTracing(true); // Disable tracing globally
    interceptorProcessorConfig.setComponentRegistryService(mock(ComponentRegistryService.class));

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      interceptorProcessorConfig.shouldIntercept(location, event);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      boolean result = interceptorProcessorConfig.shouldIntercept(location, event);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isFalse();
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for globally disabled tracing:");
    System.out.println("[DEBUG_LOG] Total iterations: " + PERFORMANCE_ITERATIONS);
    System.out.println("[DEBUG_LOG] Total time: " + totalTime + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMillis(totalTime) + " ms)");
    System.out.println("[DEBUG_LOG] Average time per call: " + avgTimePerCall + " nanoseconds ("
        + TimeUnit.NANOSECONDS.toMicros(avgTimePerCall) + " microseconds)");
    System.out.println(
        "[DEBUG_LOG] Throughput: " + (PERFORMANCE_ITERATIONS * 1_000_000_000L / totalTime) + " calls/second");

    // Performance assertion
    assertThat(avgTimePerCall)
        .as("Average execution time should be less than %d nanoseconds (%d microseconds)",
            MAX_ACCEPTABLE_AVG_TIME_NANOS, TimeUnit.NANOSECONDS.toMicros(MAX_ACCEPTABLE_AVG_TIME_NANOS))
        .isLessThan(MAX_ACCEPTABLE_AVG_TIME_NANOS);
  }
}