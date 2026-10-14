package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import com.avioconsulting.mule.opentelemetry.test.util.auth.ScopedSystemProperties;
import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.extension.api.runtime.config.ConfigurationProvider;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleBatchRestartTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public final ScopedSystemProperties properties = new ScopedSystemProperties();

  @Override
  protected String getConfigFile() {
    return "batch-config.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    properties.set("mule.otel.span.processors.enable", "true");
    properties.set("otel.traces.sampler", "always_on");
  }

  @Test
  public void batchSpansAreExportedAfterConfigurationRestart() throws Exception {
    ConfigurationProvider provider = (ConfigurationProvider) registry.lookupByName("OpenTelemetry_Config").get();
    OpenTelemetryExtensionConfiguration config = (OpenTelemetryExtensionConfiguration) provider.get(null)
        .getValue();
    OpenTelemetryConnection original = config.getOpenTelemetryConnection();

    runBatch();
    String originalTraceId = assertBatchHierarchy();

    // Restart without recreating the Mule context or resetting static state.
    config.stop();
    assertThat(original.isClosed()).isTrue();
    config.start();
    OpenTelemetryConnection replacement = config.getOpenTelemetryConnection();
    assertThat(replacement).isNotSameAs(original);
    assertThat(replacement._getSdkForTest()).isNotSameAs(original._getSdkForTest());
    assertThat(replacement.getTransactionStore()).isNotSameAs(original.getTransactionStore());
    spanQueue.clear();

    runBatch();
    String replacementTraceId = assertBatchHierarchy();
    assertThat(replacementTraceId).isNotEqualTo(originalTraceId);
    assertThat(spanQueue).extracting(DelegatedLoggingSpanTestExporter.Span::getTraceId)
        .doesNotContain(originalTraceId);
  }

  private void runBatch() throws Exception {
    flowRunner("batch-flow-1").withPayload(Collections.singletonList(1)).run();
    // Wait for the asynchronous batch: this fixture exports 22 spans per record.
    await().untilAsserted(() -> assertThat(spanQueue).hasSize(22));
  }

  private String assertBatchHierarchy() {
    DelegatedLoggingSpanTestExporter.Span batch = uniqueSpan("batch-variables-exBatch_Job");
    assertChild(batch, uniqueSpan("batch:job"));
    assertChild(uniqueSpan("batch:step:Batch_Step_1"), batch);
    assertChild(uniqueSpan("batch:step:Batch_Step_2"), batch);
    assertChild(uniqueSpan("batch:on-complete"), batch);
    assertRecordProcessor("Batch_Step_1", "logger:Batch Step 1 - Logger 1");
    assertRecordProcessor("Batch_Step_2", "logger:Batch Step 2 - Logger");
    List<DelegatedLoggingSpanTestExporter.Span> records = spanQueue.stream()
        .filter(span -> "batch:step-record".equals(span.getSpanName()))
        .collect(Collectors.toList());
    assertThat(records).hasSize(2);
    return batch.getTraceId();
  }

  private void assertRecordProcessor(String stepName, String processorName) {
    DelegatedLoggingSpanTestExporter.Span step = uniqueSpan("batch:step:" + stepName);
    List<DelegatedLoggingSpanTestExporter.Span> records = spanQueue.stream()
        .filter(span -> "batch:step-record".equals(span.getSpanName())
            && step.getSpanId().equals(span.getParentSpanContext().getSpanId()))
        .collect(Collectors.toList());
    assertThat(records).as("one record span beneath %s", stepName).hasSize(1);
    assertChild(records.get(0), step);
    assertChild(uniqueSpan(processorName), records.get(0));
  }

  private DelegatedLoggingSpanTestExporter.Span uniqueSpan(String name) {
    List<DelegatedLoggingSpanTestExporter.Span> matches = spanQueue.stream()
        .filter(span -> name.equals(span.getSpanName()))
        .collect(Collectors.toList());
    assertThat(matches).as("exactly one %s span", name).hasSize(1);
    return matches.get(0);
  }

  private void assertChild(DelegatedLoggingSpanTestExporter.Span child,
      DelegatedLoggingSpanTestExporter.Span parent) {
    assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(child.getParentSpanContext().getSpanId()).isEqualTo(parent.getSpanId());
  }
}
