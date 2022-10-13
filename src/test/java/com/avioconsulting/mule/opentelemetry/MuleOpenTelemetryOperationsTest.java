package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryOperationsTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-operations.xml";
  }

  @Test
  public void testHttpTracing_WithCustomTags() throws Exception {
    sendRequest(CORRELATION_ID, "tags", 200, Collections.emptyMap(),
        Collections.singletonMap("orderId", "order123"));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("/tags", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("http.status_code", "200")
        .containsEntry("custom.orderId", "order123")
        .containsEntry("custom.quantity", "20")
        .containsEntry("custom.payload", "Tag Payload");
  }

  @Test
  public void testHttpTracing_GetTraceContext() throws Exception {
    CoreEvent coreEvent = runFlow("mule-opentelemetry-get-trace-context");
    assertThat(coreEvent.getVariables())
        .as("Variables that should contain OTEL injected context")
        .containsKeys("OTEL_TRACE_CONTEXT", "OTEL_CONTEXT");
    TypedValue<Map<String, String>> otel_trace_context_from_interceptor = (TypedValue<Map<String, String>>) coreEvent
        .getVariables().get("OTEL_TRACE_CONTEXT");
    TypedValue<Map<String, String>> otel_context_from_operation = (TypedValue<Map<String, String>>) coreEvent
        .getVariables().get("OTEL_CONTEXT");
    assertThat(otel_trace_context_from_interceptor.getValue())
        .containsExactlyEntriesOf(otel_context_from_operation.getValue());
  }

}
