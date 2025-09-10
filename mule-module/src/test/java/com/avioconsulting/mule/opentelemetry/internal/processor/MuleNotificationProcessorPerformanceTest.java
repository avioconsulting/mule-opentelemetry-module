package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.util.MultiMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Time-based performance test for getProcessorComponent() method in
 * MuleNotificationProcessor.
 * This test measures the execution time of the getProcessorComponent() method
 * under various scenarios
 * to ensure performance characteristics remain within acceptable bounds.
 */
public class MuleNotificationProcessorPerformanceTest extends AbstractProcessorComponentTest {

  private static final int WARMUP_ITERATIONS = 1000;
  private static final int PERFORMANCE_ITERATIONS = 10000;
  private static final long MAX_ACCEPTABLE_AVG_TIME_NANOS = TimeUnit.MICROSECONDS.toNanos(15); // 15 microseconds

  @Test
  public void getProcessorComponent_performance_test_with_found_processor() {
    System.out.println("[DEBUG_LOG] Starting performance test for getProcessorComponent() with found processor");

    // Setup test data
    ComponentIdentifier identifier = getMockedIdentifier("http", "request");
    ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    when(connection.getExpressionManager()).thenReturn(null);
    when(connection.isTurnOffTracing()).thenReturn(false);

    ProcessorComponentService processorComponentService = mock(ProcessorComponentService.class);
    ProcessorComponent mockProcessorComponent = mock(ProcessorComponent.class);
    when(processorComponentService.getProcessorComponentFor(eq(identifier), any(), any()))
        .thenReturn(mockProcessorComponent);

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(false, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(componentRegistryService)
        .setProcessorComponentService(processorComponentService);
    notificationProcessor.init(connection, traceLevelConfiguration);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      notificationProcessor.getProcessorComponent(identifier);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ProcessorComponent result = notificationProcessor.getProcessorComponent(identifier);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isNotNull();
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for found processor:");
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
  public void getProcessorComponent_performance_test_with_ignored_component() {
    System.out.println("[DEBUG_LOG] Starting performance test for getProcessorComponent() with ignored component");

    // Setup test data
    ComponentIdentifier identifier = getMockedIdentifier("mule", "logger");
    ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    when(connection.getExpressionManager()).thenReturn(null);
    when(connection.isTurnOffTracing()).thenReturn(false);

    ProcessorComponentService processorComponentService = mock(ProcessorComponentService.class);
    when(processorComponentService.getProcessorComponentFor(eq(identifier), any(), any()))
        .thenReturn(null);

    List<MuleComponent> ignoredComponents = new ArrayList<>();
    ignoredComponents.add(new MuleComponent("mule", "logger"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, ignoredComponents);

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(componentRegistryService)
        .setProcessorComponentService(processorComponentService);
    notificationProcessor.init(connection, traceLevelConfiguration);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      notificationProcessor.getProcessorComponent(identifier);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ProcessorComponent result = notificationProcessor.getProcessorComponent(identifier);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isNull(); // Should be null due to ignored component
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for ignored component:");
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
  public void getProcessorComponent_performance_test_with_generic_fallback() {
    System.out.println("[DEBUG_LOG] Starting performance test for getProcessorComponent() with generic fallback");

    // Setup test data
    ComponentIdentifier identifier = getMockedIdentifier("custom", "processor");
    ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    when(connection.getExpressionManager()).thenReturn(null);
    when(connection.isTurnOffTracing()).thenReturn(false);

    ProcessorComponentService processorComponentService = mock(ProcessorComponentService.class);
    when(processorComponentService.getProcessorComponentFor(eq(identifier), any(), any()))
        .thenReturn(null); // No specific processor found

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(componentRegistryService)
        .setProcessorComponentService(processorComponentService);
    notificationProcessor.init(connection, traceLevelConfiguration);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      notificationProcessor.getProcessorComponent(identifier);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ProcessorComponent result = notificationProcessor.getProcessorComponent(identifier);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isNotNull().isInstanceOf(GenericProcessorComponent.class);
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for generic fallback:");
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
  public void getProcessorComponent_performance_test_with_additional_span_components() {
    System.out.println(
        "[DEBUG_LOG] Starting performance test for getProcessorComponent() with additional span components");

    // Setup test data
    ComponentIdentifier identifier = getMockedIdentifier("mule", "remove-variable");
    ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    when(connection.getExpressionManager()).thenReturn(null);
    when(connection.isTurnOffTracing()).thenReturn(false);

    ProcessorComponentService processorComponentService = mock(ProcessorComponentService.class);
    when(processorComponentService.getProcessorComponentFor(eq(identifier), any(), any()))
        .thenReturn(null); // No specific processor found

    List<MuleComponent> additionalSpanComponents = new ArrayList<>();
    additionalSpanComponents.add(new MuleComponent("mule", "remove-variable"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(false, Collections.emptyList(),
        additionalSpanComponents);

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(componentRegistryService)
        .setProcessorComponentService(processorComponentService);
    notificationProcessor.init(connection, traceLevelConfiguration);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      notificationProcessor.getProcessorComponent(identifier);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ProcessorComponent result = notificationProcessor.getProcessorComponent(identifier);
      // Verify the method returns expected result to ensure it's not optimized away
      assertThat(result).isNotNull().isInstanceOf(GenericProcessorComponent.class);
    }

    long endTime = System.nanoTime();
    long totalTime = endTime - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for additional span components:");
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