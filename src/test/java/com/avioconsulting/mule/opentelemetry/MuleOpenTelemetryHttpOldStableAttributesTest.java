package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryHttpOldStableAttributesTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    System.setProperty("otel.semconv-stability.opt-in", "http/dup");
    super.doSetUpBeforeMuleContextCreation();
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    System.clearProperty("otel.semconv-stability.opt-in");
    super.doTearDownAfterMuleContextDispose();
  }

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Test
  public void testOldAndStableHttpAttributes() throws Exception {
    System.setProperty("otel.semconv-stability.opt-in", "http/dup");
    sendRequest(CORRELATION_ID, "test-remote-request", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .hasSize(3));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind")
            .containsOnly("/test-remote-request", "SERVER"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .hasSizeGreaterThanOrEqualTo(13)
        .containsEntry("mule.app.flow.name", "mule-opentelemetry-app-requester-remote")
        .containsKey("mule.serverId")
        .containsEntry("http.scheme", "http")
        .containsEntry("http.method", "GET")
        .containsEntry("http.route", "/test-remote-request")
        .containsKey("mule.correlationId")
        .containsKey("http.user_agent")
        .hasEntrySatisfying("http.host",
            value -> assertThat(value.toString()).startsWith("localhost:"))
        .containsEntry("http.flavor", "1.1")
        .containsEntry("mule.app.flow.source.name", "listener")
        .containsEntry("mule.app.flow.source.namespace", "http")
        .containsEntry("mule.app.flow.source.configRef", "HTTP_Listener_config")
        .containsEntry("server.address", "localhost")
        .containsEntry("server.port", serverPort.getValue())
        .containsEntry("http.route", "/test-remote-request")
        .containsEntry("http.request.method", "GET")
        .containsEntry("url.path", "/test-remote-request")
        .doesNotContainKey("url.query")
        .containsEntry("url.scheme", "http")
        .containsKey("user_agent.original")
        .containsKey("mule.correlationId");
    System.clearProperty("otel.semconv-stability.opt-in");
  }

  private void resetStaticFinalFields() {

  }
}
