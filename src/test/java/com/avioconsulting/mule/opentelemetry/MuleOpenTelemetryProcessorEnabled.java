package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryProcessorEnabled extends AbstractMuleArtifactTraceTest {

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
            .hasSize(4)
            .extracting("spanName", "spanKind")
            .containsOnly(tuple("logger:Logger", "INTERNAL"),
                tuple("set-payload:Set Payload", "INTERNAL"),
                tuple("logger:Logger", "INTERNAL"),
                tuple("/test", "SERVER")));
  }
}
