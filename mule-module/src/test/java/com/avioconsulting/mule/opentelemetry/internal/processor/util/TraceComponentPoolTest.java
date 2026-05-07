package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TraceComponentPool} and {@link PooledTraceComponent}
 * focusing on thread-safety correctness after pool reuse.
 *
 * <p>
 * Specifically covers the bug where
 * {@link PooledTraceComponent#reset(String, String)}
 * was not calling {@link TraceComponent#clear()} first, which caused stale
 * field values
 * (name, location, contextScopedLocation, tags, etc.) to survive across pool
 * borrows
 * under concurrent load, leading to NullPointerExceptions in the span tracking
 * store.
 */
public class TraceComponentPoolTest {

  private TraceComponentPool pool;

  @Before
  public void setUp() {
    // onClose is invoked by PooledTraceComponent.close(); in these tests we call
    // pool.release() directly, so a no-op callback is sufficient.
    pool = new TraceComponentPool(component -> {
    });
    TraceComponentManager.resetForTest();
  }

  @After
  public void tearDown() {
    TraceComponentManager.resetForTest();
  }

  // ---------------------------------------------------------------------------
  // Pool reset correctness
  // ---------------------------------------------------------------------------

  @Test
  public void shouldClearAllFieldsOnReuse() {
    // Given: a component is borrowed and fully populated
    TraceComponent first = pool.acquire("tx-1", "my-flow");
    first.withLocation("my-flow/processors/0")
        .withEventContextId("ctx-abc123")
        .withSpanName("GET /api")
        .withSpanKind(SpanKind.SERVER)
        .withStartTime(Instant.now())
        .withEndTime(Instant.now())
        .withTransactionId("tx-1");
    first.addTag("key1", "value1");
    first.addTag("key2", "value2");

    // When: it is released and re-acquired
    pool.release(first);
    TraceComponent second = pool.acquire("tx-2", "other-flow");

    // Then: all fields from the prior borrow must be gone
    assertThat(second.getName()).as("name must be the new value").isEqualTo("other-flow");
    assertThat(second.getTransactionId()).as("transactionId must be the new value").isEqualTo("tx-2");
    assertThat(second.getLocation()).as("location must be null after reset").isNull();
    assertThat(second.getEventContextId()).as("eventContextId must be null after reset").isNull();
    assertThat(second.contextScopedLocation()).as("contextScopedLocation must be null after reset").isNull();
    assertThat(second.getSpanName()).as("spanName must be null after reset").isNull();
    assertThat(second.getSpanKind()).as("spanKind must be null after reset").isNull();
    assertThat(second.getEndTime()).as("endTime must be null after reset").isNull();
    assertThat(second.getContext()).as("context must be null after reset").isNull();
    assertThat(second.getReadOnlyTags()).as("tags must be empty after reset").isEmpty();
  }

  @Test
  public void shouldSetNameCorrectlyAfterReuseOfPreWarmedComponent() {
    // The pool pre-warms 50 components with (null, null). Verify that after
    // acquire the name is correctly set and not null.
    TraceComponent component = pool.acquire("tx-1", "product-configs-api-gtwy-main");

    assertThat(component.getName())
        .as("name must not be null on a pre-warmed pool component")
        .isEqualTo("product-configs-api-gtwy-main");
    assertThat(component.getTransactionId())
        .as("transactionId must not be null on a pre-warmed pool component")
        .isEqualTo("tx-1");
  }

  // ---------------------------------------------------------------------------
  // contextScopedLocation — ordering independence (Issue 2)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldComputeContextScopedLocationWhenEventContextIdSetBeforeLocation() {
    // This was the broken ordering: withEventContextId before withLocation
    // Previously contextScopedLocation stayed null because the guard
    // `if (getLocation() != null && contextScopedLocation == null)` skipped
    // the computation when location was not yet set.
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withEventContextId("ctx-abc123")
        .withLocation("my-flow");

    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be computed when eventContextId is set before location")
        .isEqualTo("ctx-abc123/my-flow");
  }

  @Test
  public void shouldComputeContextScopedLocationWhenLocationSetBeforeEventContextId() {
    // This was always working; verify it still works after the fix
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withLocation("my-flow")
        .withEventContextId("ctx-abc123");

    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be computed when location is set before eventContextId")
        .isEqualTo("ctx-abc123/my-flow");
  }

  @Test
  public void shouldRecomputeContextScopedLocationOnReuse() {
    // Given: a component is borrowed, used, and returned to the pool
    TraceComponent first = pool.acquire("tx-1", "flow-a");
    first.withLocation("flow-a").withEventContextId("ctx-111");
    assertThat(first.contextScopedLocation()).isEqualTo("ctx-111/flow-a");
    pool.release(first);

    // When: the same physical instance is re-acquired and configured for a
    // different request
    TraceComponent second = pool.acquire("tx-2", "flow-b");
    second.withEventContextId("ctx-222").withLocation("flow-b");

    // Then: contextScopedLocation must reflect the new values, not the prior
    // borrow's values
    assertThat(second.contextScopedLocation())
        .as("contextScopedLocation must not carry stale value from prior borrow")
        .isEqualTo("ctx-222/flow-b");
  }

  @Test
  public void shouldNullContextScopedLocationWhenOnlyLocationSet() {
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withLocation("my-flow");

    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be null when eventContextId is not set")
        .isNull();
  }

  @Test
  public void shouldNullContextScopedLocationWhenOnlyEventContextIdSet() {
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withEventContextId("ctx-abc123");

    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be null when location is not set")
        .isNull();
  }

  @Test
  public void shouldNullContextScopedLocationWhenEventContextIdCleared() {
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withLocation("my-flow")
        .withEventContextId("ctx-abc123");

    assertThat(component.contextScopedLocation()).isEqualTo("ctx-abc123/my-flow");

    // Clearing the event context id must also null the scoped location
    component.withEventContextId(null);
    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be null after eventContextId is cleared")
        .isNull();
  }

  @Test
  public void shouldNullContextScopedLocationWhenLocationCleared() {
    TraceComponent component = TraceComponent.of("my-flow", new HashMap<>())
        .withLocation("my-flow")
        .withEventContextId("ctx-abc123");

    assertThat(component.contextScopedLocation()).isEqualTo("ctx-abc123/my-flow");

    // Clearing location must also null the scoped location
    component.withLocation(null);
    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must be null after location is cleared")
        .isNull();
  }

  // ---------------------------------------------------------------------------
  // TraceComponentManager UUID-based tracking (Issue 5)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldTrackAndUntrackComponentWhenLocationChangesAfterCreation() {
    // This is the ghost entry bug: location changes after createTraceComponent,
    // causing the old transactionId|location key to differ at close time.
    // With UUID-based tracking, the key is immutable and close always removes
    // the correct entry.
    TraceComponentManager manager = TraceComponentManager.getInstance();
    int initialCount = manager.getActiveComponentCount();

    // Create a component with name only (no location at creation time)
    TraceComponent component = manager.createTraceComponent("tx-ghost", "my-flow");
    assertThat(manager.getActiveComponentCount())
        .as("active count must increase after create")
        .isEqualTo(initialCount + 1);

    // Change the location after creation — this previously caused key mismatch
    component.withLocation("my-flow/processors/0");

    // Close the component — with UUID keys, this should remove the correct entry
    component.close();
    assertThat(manager.getActiveComponentCount())
        .as("active count must return to initial after close, even with location change")
        .isEqualTo(initialCount);
  }

  @Test
  public void shouldTrackConcurrentComponentsWithSameTransactionIdAndLocationIndependently() {
    // This is the double-tracking overwrite bug: two components with the same
    // transactionId+location would share the same key, causing the second put()
    // to overwrite the first. With UUID-based keys, each component has a unique
    // key.
    TraceComponentManager manager = TraceComponentManager.getInstance();
    int initialCount = manager.getActiveComponentCount();

    // Create two components with identical transactionId and name
    TraceComponent comp1 = manager.createTraceComponent("tx-dup", "same-flow");
    TraceComponent comp2 = manager.createTraceComponent("tx-dup", "same-flow");

    assertThat(manager.getActiveComponentCount())
        .as("both components must be tracked independently")
        .isEqualTo(initialCount + 2);

    // Close the first component — should not affect the second's tracking
    comp1.close();
    assertThat(manager.getActiveComponentCount())
        .as("only one component removed after closing first")
        .isEqualTo(initialCount + 1);

    // Close the second component
    comp2.close();
    assertThat(manager.getActiveComponentCount())
        .as("both components removed after closing both")
        .isEqualTo(initialCount);
  }

  @Test
  public void shouldTrackAndUntrackComponentCreatedWithLocation() {
    // Verify normal tracking lifecycle for 3-arg createTraceComponent
    // (transactionId, name, location) where location is later overridden.
    TraceComponentManager manager = TraceComponentManager.getInstance();
    int initialCount = manager.getActiveComponentCount();

    // Use a mock-like ComponentLocation via the 2-arg name overload
    // then override location — simulates
    // FlowProcessorComponent.getStartTraceComponent
    TraceComponent component = manager.createTraceComponent("tx-loc", "flow-name");
    component.withLocation("flow-name"); // override to flow name like FlowProcessorComponent does

    assertThat(manager.getActiveComponentCount())
        .as("component must be tracked")
        .isEqualTo(initialCount + 1);

    component.close();
    assertThat(manager.getActiveComponentCount())
        .as("component must be untracked after close")
        .isEqualTo(initialCount);
  }

  @Test
  public void shouldRecomputeContextScopedLocationWhenLocationChanges() {
    // Verify that calling withLocation twice recomputes contextScopedLocation
    TraceComponent component = TraceComponent.of("flow", new HashMap<>())
        .withEventContextId("ctx-1")
        .withLocation("loc-a");
    assertThat(component.contextScopedLocation()).isEqualTo("ctx-1/loc-a");

    component.withLocation("loc-b");
    assertThat(component.contextScopedLocation())
        .as("contextScopedLocation must update when location changes")
        .isEqualTo("ctx-1/loc-b");
  }

  @Test
  public void shouldBeIdempotentOnDoubleClose() {
    // Verify that calling close() twice doesn't cause errors or
    // return the component to the pool twice
    TraceComponentManager manager = TraceComponentManager.getInstance();
    int initialCount = manager.getActiveComponentCount();

    TraceComponent component = manager.createTraceComponent("tx-double", "my-flow");
    assertThat(manager.getActiveComponentCount()).isEqualTo(initialCount + 1);

    component.close();
    assertThat(manager.getActiveComponentCount()).isEqualTo(initialCount);

    // Second close should be a no-op
    component.close();
    assertThat(manager.getActiveComponentCount())
        .as("double close must not cause negative tracking count")
        .isEqualTo(initialCount);
  }

  @Test
  public void shouldHandleConcurrentCreateAndClose() throws Exception {
    // Stress test: many threads creating and closing components concurrently
    // with location changes — verifying no ghost entries remain
    TraceComponentManager manager = TraceComponentManager.getInstance();
    int threads = 8;
    int opsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);
    AtomicInteger errors = new AtomicInteger(0);

    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      executor.submit(() -> {
        try {
          startLatch.await();
          for (int i = 0; i < opsPerThread; i++) {
            try (TraceComponent tc = manager.createTraceComponent(
                "tx-" + threadId + "-" + i, "flow-" + threadId)) {
              // Simulate the real pattern: change location after creation
              tc.withLocation("flow/proc/" + i);
              tc.withEventContextId("ctx-" + threadId + "-" + i);
            }
          }
        } catch (Exception e) {
          errors.incrementAndGet();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertThat(doneLatch.await(30, TimeUnit.SECONDS))
        .as("all threads must complete within timeout")
        .isTrue();

    assertThat(errors.get())
        .as("no exceptions during concurrent create/close")
        .isZero();

    assertThat(manager.getActiveComponentCount())
        .as("all components must be properly untracked after concurrent operations")
        .isZero();

    executor.shutdown();
  }
}
