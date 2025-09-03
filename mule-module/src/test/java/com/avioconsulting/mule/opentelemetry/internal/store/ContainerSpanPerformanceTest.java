package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Time-based performance test for endRouteSpans() method in ContainerSpan.
 * This test measures the execution time of the endRouteSpans() method under
 * various scenarios
 * to ensure performance characteristics remain within acceptable bounds.
 */
public class ContainerSpanPerformanceTest {

  private static final int WARMUP_ITERATIONS = 50;
  private static final int PERFORMANCE_ITERATIONS = 1000;

  private static final long MAX_ACCEPTABLE_AVG_TIME_NANOS = TimeUnit.MICROSECONDS.toNanos(250); // 225 microseconds,
  // span end takes
  // most of the time

  private ContainerSpan createTestContainerSpan() {
    Span rootSpan = mock(Span.class);
    SpanContext spanContext = mock(SpanContext.class);
    when(rootSpan.getSpanContext()).thenReturn(spanContext);
    when(spanContext.toString()).thenReturn("test-span-context");

    TraceComponent traceComponent = TraceComponent.of("test-container")
        .withTransactionId("test-transaction")
        .withSpanName("test-span")
        .withLocation("test-location");

    return new ContainerSpan("test-container", rootSpan, traceComponent);
  }

  private TraceComponent createRouterTraceComponent(String eventContextId, String location) {
    ComponentLocation componentLocation = mock(ComponentLocation.class);
    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier componentIdentifier = mock(ComponentIdentifier.class);

    when(typedComponentIdentifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.ROUTER);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(componentIdentifier);
    when(componentLocation.getComponentIdentifier()).thenReturn(typedComponentIdentifier);

    return TraceComponent.of("router-component")
        .withEventContextId(eventContextId)
        .withLocation(location)
        .withComponentLocation(componentLocation);
  }

  private TraceComponent createNonRouterTraceComponent(String eventContextId, String location) {
    ComponentLocation componentLocation = mock(ComponentLocation.class);
    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier componentIdentifier = mock(ComponentIdentifier.class);

    when(typedComponentIdentifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(componentIdentifier);
    when(componentLocation.getComponentIdentifier()).thenReturn(typedComponentIdentifier);

    return TraceComponent.of("non-router-component")
        .withEventContextId(eventContextId)
        .withLocation(location)
        .withComponentLocation(componentLocation);
  }

  private void addRouteSpansToContainer(ContainerSpan containerSpan, String baseEventContextId, String baseLocation,
      int routeCount) {
    for (int i = 0; i < routeCount; i++) {
      String routeKey = String.format("%s_12345%d/%s/route/%d", baseEventContextId, i, baseLocation, i);

      Span routeSpan = mock(Span.class);
      SpanContext routeSpanContext = mock(SpanContext.class);
      when(routeSpan.getSpanContext()).thenReturn(routeSpanContext);
      when(routeSpanContext.toString()).thenReturn("route-span-context-" + i);

      ProcessorSpan processorSpan = mock(ProcessorSpan.class);
      when(processorSpan.getSpan()).thenReturn(routeSpan);

      // Use reflection to add to childSpans map
      try {
        java.lang.reflect.Field childSpansField = ContainerSpan.class.getDeclaredField("childSpans");
        childSpansField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, ProcessorSpan> childSpans = (java.util.concurrent.ConcurrentHashMap<String, ProcessorSpan>) childSpansField
            .get(containerSpan);
        childSpans.put(routeKey, processorSpan);
      } catch (Exception e) {
        throw new RuntimeException("Failed to add route spans", e);
      }
    }
  }

  @Test
  public void endRouteSpans_performance_test_with_router_and_multiple_routes() {
    System.out.println("[DEBUG_LOG] Starting performance test for endRouteSpans() with router and multiple routes");

    // Setup test data
    ContainerSpan containerSpan = createTestContainerSpan();
    String eventContextId = "3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373";
    String location = "flow-controls:scatter-gather:sub-flow/processors/1";
    TraceComponent routerComponent = createRouterTraceComponent(eventContextId, location);
    Instant endTime = Instant.now();

    // Add multiple route spans
    addRouteSpansToContainer(containerSpan, eventContextId, location, 5);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      ContainerSpan warmupContainer = createTestContainerSpan();
      addRouteSpansToContainer(warmupContainer, eventContextId, location, 5);
      warmupContainer.endRouteSpans(routerComponent, endTime);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = 0;
    long endTimeNanos = 0;
    long totalTime = 0;
    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ContainerSpan testContainer = createTestContainerSpan();
      addRouteSpansToContainer(testContainer, eventContextId, location, 5);
      startTime = System.nanoTime();
      testContainer.endRouteSpans(routerComponent, endTime);
      endTimeNanos = System.nanoTime();
      totalTime += endTimeNanos - startTime;
    }

    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for router with multiple routes:");
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

    // Original method result
    // Test time ~11sec
    // [DEBUG_LOG] Starting performance test for endRouteSpans() with router and
    // multiple routes
    // [DEBUG_LOG] Warming up with 10 iterations
    // [DEBUG_LOG] Starting performance measurement with 100 iterations
    // [DEBUG_LOG] Performance Results for router with multiple routes:
    // [DEBUG_LOG] Total iterations: 100
    // [DEBUG_LOG] Total time: 57560371 nanoseconds (57 ms)
    // [DEBUG_LOG] Average time per call: 575603 nanoseconds (575 microseconds)
    // [DEBUG_LOG] Throughput: 1737 calls/second
    //
  }

  @Test
  public void endRouteSpans_performance_test_with_non_router_component() {
    System.out.println("[DEBUG_LOG] Starting performance test for endRouteSpans() with non-router component");

    // Setup test data
    ContainerSpan containerSpan = createTestContainerSpan();
    String eventContextId = "3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373";
    String location = "processors/logger/1";
    TraceComponent nonRouterComponent = createNonRouterTraceComponent(eventContextId, location);
    Instant endTime = Instant.now();

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      containerSpan.endRouteSpans(nonRouterComponent, endTime);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      containerSpan.endRouteSpans(nonRouterComponent, endTime);
    }

    long endTimeNanos = System.nanoTime();
    long totalTime = endTimeNanos - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for non-router component (early return):");
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
  public void endRouteSpans_performance_test_with_large_number_of_child_spans() {
    System.out
        .println("[DEBUG_LOG] Starting performance test for endRouteSpans() with large number of child spans");

    // Setup test data
    ContainerSpan containerSpan = createTestContainerSpan();
    String eventContextId = "3c2e1320-e834-11ee-bf88-da9e78fba8b6_1585670373";
    String location = "flow-controls:scatter-gather:sub-flow/processors/1";
    TraceComponent routerComponent = createRouterTraceComponent(eventContextId, location);
    Instant endTime = Instant.now();

    // Add large number of route spans (20 routes)
    addRouteSpansToContainer(containerSpan, eventContextId, location, 5);

    // Add some non-matching spans to test regex filtering performance
    int childSpanCount = 15;
    addNonMatchingSpans(containerSpan, childSpanCount);

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      ContainerSpan warmupContainer = createTestContainerSpan();
      addRouteSpansToContainer(warmupContainer, eventContextId, location, 5);
      addNonMatchingSpans(warmupContainer, childSpanCount);
      warmupContainer.endRouteSpans(routerComponent, endTime);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = 0;
    long endTimeNanos = 0;
    long totalTime = 0;
    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      ContainerSpan testContainer = createTestContainerSpan();
      addRouteSpansToContainer(testContainer, eventContextId, location, 5);
      addNonMatchingSpans(testContainer, childSpanCount);
      startTime = System.nanoTime();
      testContainer.endRouteSpans(routerComponent, endTime);
      endTimeNanos = System.nanoTime();
      totalTime += endTimeNanos - startTime;
    }

    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for large number of child spans:");
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

    // Results of original method
    // Test time 1min 37 sec
    // [DEBUG_LOG] Starting performance test for endRouteSpans() with large number
    // of child spans
    // [DEBUG_LOG] Warming up with 1000 iterations
    // [DEBUG_LOG] Starting performance measurement with 10000 iterations
    // [DEBUG_LOG] Performance Results for large number of child spans:
    // [DEBUG_LOG] Total iterations: 10000
    // [DEBUG_LOG] Total time: 85427557916 nanoseconds (85427 ms)
    // [DEBUG_LOG] Average time per call: 8542755 nanoseconds (8542 microseconds)
    // [DEBUG_LOG] Throughput: 117 calls/second
    //

  }

  @Test
  public void endRouteSpans_performance_test_with_null_component_location() {
    System.out.println("[DEBUG_LOG] Starting performance test for endRouteSpans() with null component location");

    // Setup test data
    ContainerSpan containerSpan = createTestContainerSpan();
    TraceComponent componentWithNullLocation = TraceComponent.of("null-location-component")
        .withEventContextId("test-context")
        .withLocation("test-location")
        .withComponentLocation(null); // Null component location
    Instant endTime = Instant.now();

    // Warmup phase
    System.out.println("[DEBUG_LOG] Warming up with " + WARMUP_ITERATIONS + " iterations");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      containerSpan.endRouteSpans(componentWithNullLocation, endTime);
    }

    // Performance measurement phase
    System.out
        .println("[DEBUG_LOG] Starting performance measurement with " + PERFORMANCE_ITERATIONS + " iterations");
    long startTime = System.nanoTime();

    for (int i = 0; i < PERFORMANCE_ITERATIONS; i++) {
      containerSpan.endRouteSpans(componentWithNullLocation, endTime);
    }

    long endTimeNanos = System.nanoTime();
    long totalTime = endTimeNanos - startTime;
    long avgTimePerCall = totalTime / PERFORMANCE_ITERATIONS;

    System.out.println("[DEBUG_LOG] Performance Results for null component location (early return):");
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

  private void addNonMatchingSpans(ContainerSpan containerSpan, int count) {
    for (int i = 0; i < count; i++) {
      String nonMatchingKey = String.format("non-matching-span-%d/some/other/location", i);

      Span nonMatchingSpan = mock(Span.class);
      SpanContext nonMatchingSpanContext = mock(SpanContext.class);
      when(nonMatchingSpan.getSpanContext()).thenReturn(nonMatchingSpanContext);
      when(nonMatchingSpanContext.toString()).thenReturn("non-matching-span-context-" + i);

      ProcessorSpan processorSpan = mock(ProcessorSpan.class);
      when(processorSpan.getSpan()).thenReturn(nonMatchingSpan);

      // Use reflection to add to childSpans map
      try {
        java.lang.reflect.Field childSpansField = ContainerSpan.class.getDeclaredField("childSpans");
        childSpansField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, ProcessorSpan> childSpans = (java.util.concurrent.ConcurrentHashMap<String, ProcessorSpan>) childSpansField
            .get(containerSpan);
        childSpans.put(nonMatchingKey, processorSpan);
      } catch (Exception e) {
        throw new RuntimeException("Failed to add non-matching spans", e);
      }
    }
  }
}