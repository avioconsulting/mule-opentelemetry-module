package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryDisableTracingTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("mule.otel.tracing.disabled", "true");
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
    System.clearProperty("mule.otel.tracing.disabled");
  }

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-processor-enabled.xml";
  }

  @After
  @Override
  public void clearSpansQueue() {

  }

  @Test
  public void tracingDisabledBySystemProperty() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(
        () -> assertThat(DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter.spanQueue)
            .as("Spans for listener and processors")
            .hasSize(0));
  }
}
