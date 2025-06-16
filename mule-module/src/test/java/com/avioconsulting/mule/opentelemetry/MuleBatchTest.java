package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleBatchTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "batch-config.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("mule.otel.span.processors.enable", "true");
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
    System.clearProperty("mule.otel.span.processors.enable");
  }

  @Test
  public void batchWithSpanAllTest() throws Exception {
    CoreEvent coreEvent = flowRunner("batch-flow-1")
        .withPayload(Collections.singletonList(1))
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(22));
    Map<Object, Set<String>> groupedSpans = groupSpanByNestedParentPrefix();
    System.out.println(groupedSpans);

    SoftAssertions softly = new SoftAssertions();

    // Assert the structure of grouped spans
    softly.assertThat(groupedSpans).isNotEmpty();

    // Assert batch-flow-1 parent spans
    softly.assertThat(groupedSpans.get("batch-flow-1")).containsExactlyInAnyOrder(
        "batch-flow-1",
        "set-payload:Set Payload",
        "batch:job",
        "logger:logger");

    // Assert batch:job parent spans
    softly.assertThat(groupedSpans.get("batch-flow-1>batch:job")).containsExactlyInAnyOrder(
        "batch-variables-exBatch_Job");

    // Assert batch-variables-exBatch_Job parent spans
    softly.assertThat(groupedSpans.get("batch:job>batch-variables-exBatch_Job")).containsExactlyInAnyOrder(
        "batch:step:Batch_Step_1",
        "batch:step:Batch_Step_2",
        "batch:on-complete");

    // Assert batch:step parent spans
    softly.assertThat(groupedSpans.get("batch-variables-exBatch_Job>batch:step:Batch_Step_1"))
        .containsExactlyInAnyOrder(
            "batch:step-record",
            "batch:aggregator");
    // Assert batch:step parent spans
    softly.assertThat(groupedSpans.get("batch-variables-exBatch_Job>batch:step:Batch_Step_2"))
        .containsExactlyInAnyOrder(
            "batch:step-record");

    // Assert batch:step-record parent spans for Batch_Step_1
    softly.assertThat(groupedSpans.get("batch:step:Batch_Step_1>batch:step-record")).containsExactlyInAnyOrder(
        "logger:Batch Step 1 - Logger 1",
        "flow-ref:flow-ref-batch-step-target-flow",
        "choice:Choice");

    DelegatedLoggingSpanTestExporter.Span step1 = getSpan("INTERNAL", "batch:step:Batch_Step_1");
    List<DelegatedLoggingSpanTestExporter.Span> childSpans = getChildrenTreeList(step1);

    assertThat(childSpans).filteredOn(span -> span.getSpanName().equalsIgnoreCase("batch:step-record"))
        .hasSize(1)
        .allSatisfy(
            span -> {

              // find children span for a batch record and validate hierarchy
              List<DelegatedLoggingSpanTestExporter.Span> recordChildSpans = getChildrenTreeList(span);
              Map<Object, Set<String>> recordSpans = groupSpanByParent(span, recordChildSpans);
              System.out.println(recordSpans);
              // Assert flow-ref:flow-ref-batch-step-target-flow parent spans
              softly.assertThat(recordSpans.get("flow-ref:flow-ref-batch-step-target-flow"))
                  .containsExactlyInAnyOrder(
                      "batch-step-target-flow");

              // Assert batch-step-target-flow parent spans
              softly.assertThat(recordSpans.get("batch-step-target-flow")).containsExactlyInAnyOrder(
                  "flow-ref:flow-ref-batch-step-target-sub-flow");

              // Assert flow-ref:flow-ref-batch-step-target-sub-flow parent spans
              softly.assertThat(recordSpans.get("flow-ref:flow-ref-batch-step-target-sub-flow"))
                  .containsExactlyInAnyOrder(
                      "batch-step-target-sub-flow");

              // Assert batch-step-target-sub-flow parent spans
              softly.assertThat(recordSpans.get("batch-step-target-sub-flow")).containsExactlyInAnyOrder(
                  "logger:logger");
            });

    assertThat(childSpans).filteredOn(span -> span.getSpanName().equalsIgnoreCase("batch:aggregator")).hasSize(1)
        .allSatisfy(span -> {
          List<DelegatedLoggingSpanTestExporter.Span> recordChildSpans = getChildrenTreeList(span);
          Map<Object, Set<String>> aggrSpans = groupSpanByParent(span, recordChildSpans);
          System.out.println(aggrSpans);

          // Assert batch:aggregator parent spans
          softly.assertThat(aggrSpans.get("batch:aggregator")).containsExactlyInAnyOrder(
              "logger:Batch Step 1 - Logger Aggregator 1");
        });

    // Assert batch:on-complete parent spans
    softly.assertThat(groupedSpans.get("batch-variables-exBatch_Job>batch:on-complete")).containsExactlyInAnyOrder(
        "logger:Batch On Complete - Logger");

    softly.assertAll();
  }

  @Test
  public void batchWithSpanTest_Step1Error() throws Exception {
    CoreEvent coreEvent = flowRunner("batch-flow-1")
        .withPayload(Collections.singletonList(2))
        .withVariable("failureNumber", 2)
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(18));
    Map<Object, Set<String>> groupedSpans = groupSpanByNestedParentPrefix();
    System.out.println(groupedSpans);

    SoftAssertions softly = new SoftAssertions();

    // Assert the structure of grouped spans
    softly.assertThat(groupedSpans).isNotEmpty();

    // Assert batch-flow-1 parent spans
    softly.assertThat(groupedSpans.get("batch-flow-1")).containsExactlyInAnyOrder(
        "batch-flow-1",
        "set-payload:Set Payload",
        "batch:job",
        "logger:logger");

    // Assert batch:job parent spans
    softly.assertThat(groupedSpans.get("batch-flow-1>batch:job")).containsExactlyInAnyOrder(
        "batch-variables-exBatch_Job");

    // Assert batch-variables-exBatch_Job parent spans
    softly.assertThat(groupedSpans.get("batch:job>batch-variables-exBatch_Job")).containsExactlyInAnyOrder(
        "batch:step:Batch_Step_1",
        "batch:on-complete");

    // Assert batch:step parent spans
    softly.assertThat(groupedSpans.get("batch-variables-exBatch_Job>batch:step:Batch_Step_1"))
        .containsExactlyInAnyOrder(
            "batch:step-record");

    // Assert batch:step-record parent spans for Batch_Step_1
    softly.assertThat(groupedSpans.get("batch:step:Batch_Step_1>batch:step-record")).containsExactlyInAnyOrder(
        "logger:Batch Step 1 - Logger 1",
        "flow-ref:flow-ref-batch-step-target-flow",
        "choice:Choice");

    DelegatedLoggingSpanTestExporter.Span step1 = getSpan("INTERNAL", "batch:step:Batch_Step_1");
    List<DelegatedLoggingSpanTestExporter.Span> childSpans = getChildrenTreeList(step1);

    assertThat(childSpans).filteredOn(span -> span.getSpanName().equalsIgnoreCase("batch:step-record"))
        .hasSize(1)
        .allSatisfy(
            span -> {

              // find children span for a batch record and validate hierarchy
              List<DelegatedLoggingSpanTestExporter.Span> recordChildSpans = getChildrenTreeList(span);
              Map<Object, Set<String>> recordSpans = groupSpanByParent(span, recordChildSpans);
              System.out.println(recordSpans);
              // Assert flow-ref:flow-ref-batch-step-target-flow parent spans
              softly.assertThat(recordSpans.get("flow-ref:flow-ref-batch-step-target-flow"))
                  .containsExactlyInAnyOrder(
                      "batch-step-target-flow");

              // Assert batch-step-target-flow parent spans
              softly.assertThat(recordSpans.get("batch-step-target-flow")).containsExactlyInAnyOrder(
                  "flow-ref:flow-ref-batch-step-target-sub-flow");

              // Assert flow-ref:flow-ref-batch-step-target-sub-flow parent spans
              softly.assertThat(recordSpans.get("flow-ref:flow-ref-batch-step-target-sub-flow"))
                  .containsExactlyInAnyOrder(
                      "batch-step-target-sub-flow");

              // Assert batch:aggregator parent spans
              softly.assertThat(recordSpans.get("choice:Choice")).containsExactlyInAnyOrder(
                  "batch-flow-1/processors/1/route/0/route/0/processors/2/route/0");

              // Assert batch:aggregator parent spans
              softly.assertThat(
                  recordSpans.get("batch-flow-1/processors/1/route/0/route/0/processors/2/route/0"))
                  .containsExactlyInAnyOrder(
                      "raise-error:Batch Step 1 - Raise error");
            });

    // Assert batch:on-complete parent spans
    softly.assertThat(groupedSpans.get("batch-variables-exBatch_Job>batch:on-complete")).containsExactlyInAnyOrder(
        "logger:Batch On Complete - Logger");

    softly.assertAll();
  }
}
