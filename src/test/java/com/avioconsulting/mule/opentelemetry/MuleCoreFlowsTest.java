package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.SoftAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.*;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

public class MuleCoreFlowsTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-core-flows.xml";
  }

  @Test
  public void testFlowControls() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(16));
  }

  @Test
  public void testFlowControls_Choice() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:choice-\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(8));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-controls:choice-\\get-value", val -> assertThat(val)
            .contains(
                "flow-controls:choice-\\get-value",
                "logger:FirstLogger",
                "choice:Choice-Control"));
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("choice:Choice-Control", val -> assertThat(val)
            .containsAnyOf(
                "flow-controls:choice-\\get-value/processors/1/route/0",
                "flow-controls:choice-\\get-value/processors/1/route/1"));
    if (groupedSpans.containsKey("flow-controls:choice-\\get-value/processors/1/route/0")) {
      softly.assertThat(groupedSpans)
          .hasEntrySatisfying("flow-controls:choice-\\get-value/processors/1/route/0", val -> assertThat(val)
              .contains(
                  "logger:ChoiceWhen", "flow-ref:flow-ref"));
    } else {
      softly.assertThat(groupedSpans)
          .hasEntrySatisfying("flow-controls:choice-\\get-value/processors/1/route/1", val -> assertThat(val)
              .contains(
                  "logger:ChoiceDefault", "flow-ref:flow-ref"));
    }
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-ref:flow-ref", val -> assertThat(val)
            .contains(
                "simple-flow-logger"));
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("simple-flow-logger", val -> assertThat(val)
            .contains(
                "logger:SimpleLogger"));
    softly.assertAll();
  }

  @Test
  public void testFlowControls_FirstSuccessful() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:first-successful-\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(5));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-controls:first-successful-\\get-value", val -> assertThat(val)
            .contains(
                "flow-controls:first-successful-\\get-value",
                "logger:FirstLogger",
                "first-successful:First-Successful-Control"));
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("first-successful:First-Successful-Control", val -> assertThat(val)
            .containsAnyOf(
                "flow-controls:first-successful-\\get-value/processors/1/route/0",
                "flow-controls:first-successful-\\get-value/processors/1/route/1"));
    if (groupedSpans.containsKey("flow-controls:first-successful-\\get-value/processors/1/route/0")) {
      softly.assertThat(groupedSpans)
          .hasEntrySatisfying("flow-controls:first-successful-\\get-value/processors/1/route/0",
              val -> assertThat(val)
                  .contains(
                      "logger:FirstSuccess1"));
    } else {
      softly.assertThat(groupedSpans)
          .hasEntrySatisfying("flow-controls:first-successful-\\get-value/processors/1/route/1",
              val -> assertThat(val)
                  .contains(
                      "logger:FirstSuccess2"));
    }
    softly.assertAll();
  }

  @Test
  public void testFlowControls_ScatterGather() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:scatter-gather:\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(23));

    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("scatter-gather:Scatter-Gather-Control", val -> assertThat(val)
            .contains(
                "flow-controls:scatter-gather:\\get-value/processors/1/route/0",
                "flow-controls:scatter-gather:\\get-value/processors/1/route/1",
                "flow-controls:scatter-gather:\\get-value/processors/1/route/2"));
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("scatter-gather:sub-flow:Scatter-Gather-Control", val -> assertThat(val)
            .contains(
                "flow-controls:scatter-gather:sub-flow/processors/1/route/0",
                "flow-controls:scatter-gather:sub-flow/processors/1/route/1"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-controls:scatter-gather:sub-flow/processors/1/route/1",
        val -> assertThat(val).contains("logger:ScatterGather3.1.2.1", "logger:ScatterGather3.1.2.2"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-ref:flow-controls:scatter-gather:sub-flow",
        val -> assertThat(val).contains("flow-controls:scatter-gather:sub-flow"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-controls:scatter-gather:sub-flow",
        val -> assertThat(val).contains("scatter-gather:sub-flow:Scatter-Gather-Control", "logger:LastLogger3",
            "logger:FirstLogger3"));
    softly.assertThat(groupedSpans).hasEntrySatisfying(
        "flow-controls:scatter-gather:\\get-value/processors/1/route/0",
        val -> assertThat(val).contains("logger:ScatterGather1.2", "logger:ScatterGather1.1"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-controls:scatter-gather:sub-flow/processors/1/route/0",
        val -> assertThat(val).contains("logger:ScatterGather3.1.2", "logger:ScatterGather3.1.1"));
    softly.assertThat(groupedSpans).hasEntrySatisfying(
        "flow-controls:scatter-gather:\\get-value/processors/1/route/1",
        val -> assertThat(val).contains("logger:ScatterGather2.1", "logger:ScatterGather2.2"));
    softly.assertThat(groupedSpans).hasEntrySatisfying(
        "flow-controls:scatter-gather:\\get-value/processors/1/route/2",
        val -> assertThat(val).contains("logger:ScatterGather3.1",
            "flow-ref:flow-controls:scatter-gather:sub-flow"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-controls:scatter-gather:\\get-value",
        val -> assertThat(val).contains("scatter-gather:Scatter-Gather-Control", "logger:FirstLogger",
            "flow-controls:scatter-gather:\\get-value", "logger:LastLogger"));
    softly.assertThat((Integer) groupedSpans.values().stream().mapToInt(Set::size).sum())
        .as("Total grouped span count")
        .isEqualTo(spanQueue.size());
    softly.assertThat(groupedSpans).as("Number of keys asserted").hasSize(10);

    softly.assertAll();
  }

  @NotNull
  private Map<Object, Set<String>> groupSpanByParent() {
    // Find the root span
    DelegatedLoggingSpanTestExporter.Span root = spanQueue.stream()
        .filter(span -> span.getParentSpanContext().getSpanId().equals("0000000000000000")).findFirst().get();

    // Create a lookup of span id and name
    Map<String, String> idNameMap = spanQueue.stream().collect(Collectors.toMap(
        DelegatedLoggingSpanTestExporter.Span::getSpanId, DelegatedLoggingSpanTestExporter.Span::getSpanName));

    Map<Object, Set<String>> groupedSpans = spanQueue.stream()
        .collect(Collectors.groupingBy(
            span -> idNameMap.getOrDefault(span.getParentSpanContext().getSpanId(), root.getSpanName()),
            Collectors.mapping(DelegatedLoggingSpanTestExporter.Span::getSpanName, Collectors.toSet())));
    return groupedSpans;
  }

  @Test
  @Ignore
  public void testWithCorrelationId() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-scope-with-correlation-id")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(20));
  }

  @Test
  public void testRouter_RoundRobin() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:round-robin:\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(6));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-controls:round-robin:\\get-value", val -> assertThat(val)
            .contains(
                "flow-controls:round-robin:\\get-value",
                "logger:FirstLogger",
                "logger:LastLogger",
                "round-robin:Round-Robin-Control"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("round-robin:Round-Robin-Control",
        val -> assertThat(val).contains("flow-controls:round-robin:\\get-value/processors/1/route/0"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-controls:round-robin:\\get-value/processors/1/route/0",
        val -> assertThat(val).contains("logger:RoundRobin1"));
    softly.assertAll();
  }

  @Test
  public void testScopes_foreach() throws Exception {
    CoreEvent coreEvent = flowRunner("mule-core-flows-scope_foreach")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(24));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("mule-core-flows-scope_foreach", val -> assertThat(val)
            .containsOnly(
                "mule-core-flows-scope_foreach",
                "foreach:For Each",
                "logger:FirstLogger",
                "logger:LastLogger"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("foreach:For Each",
        val -> assertThat(val).containsOnly("/test-simple", "logger:ForEachLogger"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("/test-simple",
        val -> assertThat(val).containsOnly("GET /test-simple"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("GET /test-simple",
        val -> assertThat(val).containsOnly("set-payload:Set Payload"));
  }

  @Test
  public void testScopes_async() throws Exception {
    CoreEvent coreEvent = flowRunner("mule-core-flows-async-scope")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(7));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("mule-core-flows-async-scope", val -> assertThat(val)
            .containsOnly(
                "mule-core-flows-async-scope",
                "logger:FirstLogger",
                "async:Async"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("async:Async",
        val -> assertThat(val).containsOnly("logger:AsyncLogger", "/test-simple"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("/test-simple",
        val -> assertThat(val).containsOnly("GET /test-simple"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("GET /test-simple",
        val -> assertThat(val).containsOnly("set-payload:Set Payload"));
    softly.assertAll();
  }

  @Test
  public void testFlowErrorPropagationSpans() throws Exception {
    Exception muleException = catchThrowableOfType(() -> flowRunner("mule-core-flow-1")
        .run(), Exception.class);
    assertThat(muleException).hasMessage("Random failure").isNotNull();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(9));
    assertThat(spanQueue)
        .extracting("spanName")
        .contains("mule-core-flow-1", "mule-core-flow-2", "mule-core-flow-3");
    assertThat(getSpan("SERVER", "mule-core-flow-3").getAttributes())
        .containsEntry("error.type", "org.mule.runtime.core.internal.exception.MessagingException");
  }

  @Test
  public void testDynamicFlowRefFlowPropagation() throws Exception {
    CoreEvent event = flowRunner("call-dynamic-flow-ref").withVariable("targetFlow", "simple-flow-to-flow").run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(8));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("call-dynamic-flow-ref", val -> assertThat(val)
            .containsOnly(
                "logger:ParentFirstLogger", "call-dynamic-flow-ref", "flow-ref:target-flow-call"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-ref:target-flow-call",
        val -> assertThat(val).containsOnly("simple-flow-to-flow"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("simple-flow-to-flow",
        val -> assertThat(val).containsOnly("logger:FirstSimpleLogger", "flow-ref:flow-ref"));
    softly.assertAll();
  }

  @Test
  public void testDynamicFlowRefSubFlowPropagation() throws Exception {
    CoreEvent event = flowRunner("call-dynamic-flow-ref")
        .withVariable("targetFlow", "simple-subflow-logger").run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(5));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("call-dynamic-flow-ref", val -> assertThat(val)
            .containsOnly(
                "logger:ParentFirstLogger", "call-dynamic-flow-ref", "flow-ref:target-flow-call"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("flow-ref:target-flow-call",
        val -> assertThat(val).containsOnly("simple-subflow-logger"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("simple-subflow-logger",
        val -> assertThat(val).containsOnly("logger:SimpleLogger"));
    softly.assertAll();
  }

}
