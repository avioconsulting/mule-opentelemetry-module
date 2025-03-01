package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.Span;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryProcessorEnabledTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-processor-enabled.xml";
  }

  @Test
  public void testProcessorTracing() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .as("Spans for listener and processors")
            .hasSize(4)
            .extracting("spanName", "spanKind")
            .containsOnly(tuple("logger:Logger", "INTERNAL"),
                tuple("set-payload:Set Payload", "INTERNAL"),
                tuple("logger:Logger", "INTERNAL"),
                tuple("GET /test", "SERVER")));
  }

  /**
   * Disabled Mule functional test is unable to recognize `name` parameter
   * keeps throwing `Parameter 'name' is required but was not found`
   * 
   * @throws Exception
   */
  @Test
  @Ignore
  public void testProcessorSkipping() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "otel-processor-flow", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .as("Spans for listener and processors")
            .hasSize(2)
            .extracting("spanName", "spanKind")
            .containsOnly(tuple("set-payload:Set Payload", "INTERNAL"),
                tuple("GET /otel-processor-flow", "SERVER")));
  }

  @Test
  public void testFlowRefParentTraces() throws Exception {
    sendRequest(CORRELATION_ID, "/test/remote/flow-ref", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .isNotEmpty());
    Span head = DelegatedLoggingSpanTestExporter.spanQueue
        .peek();

    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .hasSize(8)
            .anySatisfy(span -> {
              assertThat(span)
                  .as("Span for http:listener source flow")
                  .extracting("spanName", "spanKind", "traceId")
                  .containsOnly("GET /test/remote/flow-ref", "SERVER", head.getTraceId());
            }));
    Span sourceServer = getSpan("SERVER", "GET /test/remote/flow-ref");

    Span flowRefTargetServer = getSpan("INTERNAL", "flow-ref:mule-opentelemetry-app-flow-ref-target");
    Span targetServer = getSpan("INTERNAL", "mule-opentelemetry-app-flow-ref-target");
    assertParentSpan(flowRefTargetServer, "Flow ref of target 1 should have source as parent", sourceServer);
    assertParentSpan(targetServer, "Parent flow must be a span of flow-ref of first target", flowRefTargetServer);

    Span flowRefTargetServer2 = getSpan("INTERNAL", "flow-ref:mule-opentelemetry-app-flow-ref-target-2");
    Span targetServer2 = getSpan("INTERNAL", "mule-opentelemetry-app-flow-ref-target-2");
    assertParentSpan(flowRefTargetServer2, "Flow ref of target 2 should have target 1 as parent", targetServer);
    assertParentSpan(targetServer2, "Parent flow must be a span of flow-ref of second target",
        flowRefTargetServer2);

    Span setPayload = getSpan("INTERNAL", "set-payload:Set Payload");
    assertParentSpan(setPayload, "Parent must be flow-ref's target flow span", targetServer);

    Span loggerSpan = getSpan("INTERNAL", "logger:Logger");
    assertParentSpan(loggerSpan, "Parent must be main root flow span", sourceServer);

    Span osClear = getSpan("INTERNAL", "clear:Clear");
    assertParentSpan(osClear, "Parent must be previous flow span", targetServer2);
  }

  private static void assertParentSpan(Span childSpan, String description, Span parentSpan) {
    assertThat(childSpan.getParentSpanContext())
        .extracting("traceId", "spanId")
        .as(description)
        .containsExactly(parentSpan.getTraceId(), parentSpan.getSpanId());
  }

}
