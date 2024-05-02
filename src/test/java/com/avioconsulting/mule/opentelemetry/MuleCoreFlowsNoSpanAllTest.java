package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.SoftAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

public class MuleCoreFlowsNoSpanAllTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-core-flows.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("mule.otel.span.processors.enable", "false");
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
    System.clearProperty("mule.otel.span.processors.enable");
  }

  @Test
  public void testFlowControls_Choice() throws Exception {
    CoreEvent coreEvent = flowRunner("flow-controls:choice-\\get-value")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(5));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-controls:choice-\\get-value", val -> assertThat(val)
            .contains(
                "flow-controls:choice-\\get-value",
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
                  "flow-ref:flow-ref"));
    } else {
      softly.assertThat(groupedSpans)
          .hasEntrySatisfying("flow-controls:choice-\\get-value/processors/1/route/1", val -> assertThat(val)
              .contains(
                  "flow-ref:flow-ref"));
    }
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("flow-ref:flow-ref", val -> assertThat(val)
            .contains(
                "simple-flow-logger"));
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

}
