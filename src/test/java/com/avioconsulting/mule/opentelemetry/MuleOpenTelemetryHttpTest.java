package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryHttpTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Test
  public void testValidHttpTracing() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind")
        .containsOnly("/test", "SERVER"));
  }

  @Test
  public void testTraceContextExtraction() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/propagation/source", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .isNotEmpty());
    DelegatedLoggingSpanExporterProvider.Span head = DelegatedLoggingSpanExporter.spanQueue.peek();

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSize(3)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/test/propagation/source", "SERVER", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/test/propagation/target", "CLIENT", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/test/propagation/target", "SERVER", head.getTraceId());
        }));
  }

  @Test
  public void testInvalidHttpRequest() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test-invalid-request", 500);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .isNotEmpty());
    DelegatedLoggingSpanExporterProvider.Span head = DelegatedLoggingSpanExporter.spanQueue.peek();

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSize(2)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/test-invalid-request", "SERVER", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/remote/invalid", "CLIENT", head.getTraceId());
        }));

  }

  @Test
  public void testHttpAttributes() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "test-remote-request", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .isNotEmpty()
        .hasSize(3));
    DelegatedLoggingSpanExporterProvider.Span head = DelegatedLoggingSpanExporter.spanQueue.peek();
    assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind", "traceId")
            .containsOnly("/test-remote-request", "SERVER", head.getTraceId()))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .hasSize(9)
        .containsEntry("mule.app.flow.name", "mule-opentelemetry-app-requester-remote")
        .containsKey("mule.serverId")
        .containsEntry("http.scheme", "http")
        .containsEntry("http.method", "GET")
        .containsEntry("http.route", "/test-remote-request")
        .containsKey("http.user_agent")
        .hasEntrySatisfying("http.host",
            value -> assertThat(value.toString()).startsWith("localhost:"))
        .containsEntry("http.flavor", "1.1");
    assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind", "traceId")
            .containsOnly("/test/remote/target", "CLIENT", head.getTraceId()))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .containsEntry("mule.app.processor.name", "request")
        .containsEntry("mule.app.processor.namespace", "http")
        .containsEntry("mule.app.processor.docName", "Request")
        // .containsEntry("http.host", "0.0.0.0:".concat(serverPort.getValue()))
        // .containsEntry("http.scheme", "http")
        .containsEntry("http.status_code", "200")
        .containsEntry("http.response_content_length", "18")
        // .containsEntry("net.peer.name", "0.0.0.0")
        // .containsEntry("net.peer.port", serverPort.getValue())
        .containsEntry("mule.app.processor.configRef", "SELF_HTTP_Request_configuration")
        .containsEntry("http.method", "GET")
        .containsEntry("http.route", "/test/remote/target");
  }

  @Test
  public void testServer400Response() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/error/400", 400);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSize(1)
        .element(0).as("Span for http:listener flow")
        .extracting("spanName", "spanKind")
        .containsOnly("/test/error/400", "SERVER"));
  }

  @Test
  public void testRequestSpanWithoutBasePath() throws Exception {
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-2-private-Flow-requester-error"));
    assertThat(exception)
        .isNotNull()
        .hasMessage("HTTP GET on resource 'http://0.0.0.0:9080/remote/invalid' failed: Connection refused.");

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .extracting("spanName", "spanKind")
              .containsExactly("/remote/invalid", "CLIENT");
        }));
  }

  @Test
  @Ignore(value = "Individual run of this test succeeds but when run in suite, it fails with error 'BeanFactory not initialized or already closed - call 'refresh' before accessing beans via the ApplicationContext'. TODO: Find root cause and enable test.")
  public void testRequestSpanWithBasePath() throws Exception {
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-2-private-Flow-requester_basepath"));
    assertThat(exception)
        .isNotNull()
        .hasMessage(
            "HTTP GET on resource 'http://0.0.0.0:9085/api/remote/invalid' failed: Connection refused.");

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .extracting("spanName", "spanKind")
              .containsExactly("/api/remote/invalid", "CLIENT");
        }));
  }
}
