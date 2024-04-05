package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

@Ignore
public class OpenTelemetrySamplingTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("mule.otel.span.processors.enable", "false");
    System.setProperty("otel.traces.sampler", "parentbased_traceidratio");
    System.setProperty("otel.traces.sampler.arg", "0.0");
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

  public void testProcessorTracing() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .as("Spans for listener and processors")
            .hasSize(1)
            .extracting("spanName", "spanKind")
            .containsOnly(tuple("GET /test", "SERVER")));
  }

  /**
   * This test will result in using the
   * {@link com.avioconsulting.mule.opentelemetry.internal.processor.MuleCoreProcessorComponent}
   * for core spans when generic spans are disabled.
   */
  public void testFlowRefParentTracesWithoutAllSpans() throws Exception {
    sendRequest(CORRELATION_ID, "/test/remote/flow-ref", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .isNotEmpty());
    DelegatedLoggingSpanTestExporter.Span head = DelegatedLoggingSpanTestExporter.spanQueue
        .peek();

    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .hasSize(5)
            .anySatisfy(span -> {
              assertThat(span)
                  .as("Span for http:listener source flow")
                  .extracting("spanName", "spanKind", "traceId")
                  .containsOnly("GET /test/remote/flow-ref", "SERVER", head.getTraceId());
            }));
    DelegatedLoggingSpanTestExporter.Span sourceServer = getSpan("SERVER", "GET /test/remote/flow-ref");

    DelegatedLoggingSpanTestExporter.Span flowRefTargetServer = getSpan("INTERNAL",
        "flow-ref:mule-opentelemetry-app-flow-ref-target");
    DelegatedLoggingSpanTestExporter.Span targetServer = getSpan("SERVER",
        "mule-opentelemetry-app-flow-ref-target");
    assertParentSpan(flowRefTargetServer, "Flow ref of target 1 should have source as parent", sourceServer);
    assertParentSpan(targetServer, "Parent flow must be a span of flow-ref of first target", flowRefTargetServer);

    DelegatedLoggingSpanTestExporter.Span flowRefTargetServer2 = getSpan("INTERNAL",
        "flow-ref:mule-opentelemetry-app-flow-ref-target-2");
    DelegatedLoggingSpanTestExporter.Span targetServer2 = getSpan("SERVER",
        "mule-opentelemetry-app-flow-ref-target-2");
    assertParentSpan(flowRefTargetServer2, "Flow ref of target 2 should have target 1 as parent", targetServer);
    assertParentSpan(targetServer2, "Parent flow must be a span of flow-ref of second target",
        flowRefTargetServer2);
  }

  private static void assertParentSpan(DelegatedLoggingSpanTestExporter.Span childSpan, String description,
      DelegatedLoggingSpanTestExporter.Span parentSpan) {
    assertThat(childSpan.getParentSpanContext())
        .extracting("traceId", "spanId")
        .as(description)
        .containsExactly(parentSpan.getTraceId(), parentSpan.getSpanId());
  }

}
