package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryProcessorPropertyOverrideTest extends AbstractMuleArtifactTraceTest {

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

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-processor-enabled.xml";
  }

  @Test
  public void testProcessorTracing() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue)
            .as("Spans for listener and processors")
            .hasSize(1)
            .extracting("spanName", "spanKind")
            .containsOnly(tuple("/test", "SERVER")));
  }

  /**
   * This test will result in using the
   * {@link com.avioconsulting.mule.opentelemetry.internal.processor.MuleCoreProcessorComponent}
   * for core spans when generic spans are disabled.
   */
  @Test
  public void testFlowRefParentTracesWithoutAllSpans() throws Exception {
    sendRequest(CORRELATION_ID, "/test/remote/flow-ref", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue)
            .isNotEmpty());
    DelegatedLoggingSpanExporterProvider.Span head = DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue
        .peek();

    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue)
            .hasSize(5)
            .anySatisfy(span -> {
              assertThat(span)
                  .as("Span for http:listener source flow")
                  .extracting("spanName", "spanKind", "traceId")
                  .containsOnly("/test/remote/flow-ref", "SERVER", head.getTraceId());
            }));
    DelegatedLoggingSpanExporterProvider.Span sourceServer = getSpan("SERVER", "/test/remote/flow-ref");

    DelegatedLoggingSpanExporterProvider.Span flowRefTargetServer = getSpan("INTERNAL",
        "flow-ref:mule-opentelemetry-app-flow-ref-target");
    DelegatedLoggingSpanExporterProvider.Span targetServer = getSpan("SERVER",
        "mule-opentelemetry-app-flow-ref-target");
    assertParentSpan(flowRefTargetServer, "Flow ref of target 1 should have source as parent", sourceServer);
    assertParentSpan(targetServer, "Parent flow must be a span of flow-ref of first target", flowRefTargetServer);

    DelegatedLoggingSpanExporterProvider.Span flowRefTargetServer2 = getSpan("INTERNAL",
        "flow-ref:mule-opentelemetry-app-flow-ref-target-2");
    DelegatedLoggingSpanExporterProvider.Span targetServer2 = getSpan("SERVER",
        "mule-opentelemetry-app-flow-ref-target-2");
    assertParentSpan(flowRefTargetServer2, "Flow ref of target 2 should have target 1 as parent", targetServer);
    assertParentSpan(targetServer2, "Parent flow must be a span of flow-ref of second target",
        flowRefTargetServer2);
  }

  private static void assertParentSpan(DelegatedLoggingSpanExporterProvider.Span childSpan, String description,
      DelegatedLoggingSpanExporterProvider.Span parentSpan) {
    assertThat(childSpan.getParentSpanContext())
        .extracting("traceId", "spanId")
        .as(description)
        .containsExactly(parentSpan.getTraceId(), parentSpan.getSpanId());
  }

  private static DelegatedLoggingSpanExporterProvider.Span getSpan(String INTERNAL, String spanName) {
    return DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue
        .stream()
        .filter(s -> s.getSpanKind().equals(INTERNAL) && s.getSpanName().equals(spanName))
        .findFirst().get();
  }
}
