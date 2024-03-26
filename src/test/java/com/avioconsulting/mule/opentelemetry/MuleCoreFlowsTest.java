package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.SoftAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
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
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(16));
  }

  @Test
  public void testFlowControls_ScatterGather() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:scatter-gather:\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(23));

    Map<Object, Set<String>> groupedSpans = groupSpanByParent();

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
        .isEqualTo(DelegatedLoggingSpanTestExporter.spanQueue.size());
    softly.assertThat(groupedSpans).as("Number of keys asserted").hasSize(10);

    softly.assertAll();
  }

  @NotNull
  private Map<Object, Set<String>> groupSpanByParent() {
    // Find the root span
    DelegatedLoggingSpanTestExporter.Span root = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getParentSpanContext().getSpanId().equals("0000000000000000")).findFirst().get();

    // Create a lookup of span id and name
    Map<String, String> idNameMap = DelegatedLoggingSpanTestExporter.spanQueue.stream().collect(Collectors.toMap(
        DelegatedLoggingSpanTestExporter.Span::getSpanId, DelegatedLoggingSpanTestExporter.Span::getSpanName));

    Map<Object, Set<String>> groupedSpans = DelegatedLoggingSpanTestExporter.spanQueue.stream()
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
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(20));
  }

  @Test
  public void testRouter_RoundRobin() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:round-robin:\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
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
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(14));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
  }

}
