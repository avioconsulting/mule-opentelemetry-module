package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryHttpTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Test
  public void testHttpTracing_WithWildCardListener() throws Exception {
    sendRequest(CORRELATION_ID, "/test-wildcard/a/b/c", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test-wildcard/*", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("http.response.status_code", 200L);
  }

  @Test
  public void testHttpTracing_WithJsonStatus_Number() throws Exception {
    sendRequest(CORRELATION_ID, "/test-json-number-status", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test-json-number-status", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("http.response.status_code", 200L);
  }

  @Test
  public void testHttpTracing_WithJsonStatus_String() throws Exception {
    sendRequest(CORRELATION_ID, "/test-json-string-status", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test-json-string-status", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("http.response.status_code", 200L);
  }

  @Test
  public void testHttpTracing_WithBadStatusStillClosesSpan() throws Exception {
    sendRequest(CORRELATION_ID, "/test-bad-status", -1);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test-bad-status", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .doesNotContainKey("http.response.status_code");
  }

  @Test
  public void testHttpTracing_WithResponseStatusCode() throws Exception {
    sendRequest(CORRELATION_ID, "test", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("http.response.status_code", 200L);
  }

  @Test
  public void testInstrumentationMetadataDetails() throws Exception {
    sendRequest(CORRELATION_ID, "test", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("instrumentationVersion", InstanceOfAssertFactories.STRING)
        .isNotNull()
        .isNotEmpty()
        .as("Version loaded from properties")
        .isNotEqualTo("0.0.1-DEV");
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("instrumentationName", InstanceOfAssertFactories.STRING)
        .isNotNull()
        .isEqualTo("mule-opentelemetry-module");
  }

  @Test
  public void testHttpTracing_WithErrorResponseStatusCode() throws Exception {
    sendRequest(CORRELATION_ID, "/test/error-status", 500);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test/error-status", "SERVER", "ERROR"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("http.response.status_code", 500L)
        .containsEntry("error.type", "org.mule.runtime.core.internal.exception.MessagingException");
  }

  @Test
  public void testHttpTracing_WithNoStatusCode() throws Exception {
    sendRequest(CORRELATION_ID, "/test/no-status", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind", "spanStatus")
        .containsOnly("GET /test/no-status", "SERVER", "UNSET"));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, Object.class))
        .doesNotContainKey("http.response.status_code");
  }

  @Test
  public void testTraceContextExtractionLoop() throws Exception {
    int requestCount = 100;
    for (int i = 0; i < requestCount; i++) {
      sendRequest(CORRELATION_ID, "/test/propagation/source", 200);
    }
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .hasSize(requestCount * 3));
  }

  @Test
  public void testTraceContextExtraction() throws Exception {
    sendRequest(CORRELATION_ID, "/test/propagation/source", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty());
    DelegatedLoggingSpanTestExporter.Span head = DelegatedLoggingSpanTestExporter.spanQueue.peek();

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(3)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("GET /test/propagation/source", "SERVER", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("/test/propagation/target", "CLIENT", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("GET /test/propagation/target", "SERVER", head.getTraceId());
        }));
    DelegatedLoggingSpanTestExporter.Span sourceServer = getSpan("SERVER", "GET /test/propagation/source");
    DelegatedLoggingSpanTestExporter.Span client = getSpan("CLIENT", "/test/propagation/target");
    DelegatedLoggingSpanTestExporter.Span targetServer = getSpan("SERVER", "GET /test/propagation/target");

    assertThat(targetServer.getParentSpanContext())
        .extracting("traceId", "spanId")
        .as("With source server span (" + sourceServer.getSpanId()
            + "), target server span should have client span as parent")
        .containsExactly(client.getTraceId(), client.getSpanId());
  }

  @Test
  public void testInvalidHttpRequest() throws Exception {
    sendRequest(CORRELATION_ID, "test-invalid-request", 500);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty());
    DelegatedLoggingSpanTestExporter.Span head = DelegatedLoggingSpanTestExporter.spanQueue.peek();

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(2)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("GET /test-invalid-request", "SERVER", head.getTraceId());
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request")
              .extracting("spanName", "spanKind", "traceId", "spanStatus", "spanStatusDescription")
              .containsOnly("/remote/invalid", "CLIENT", head.getTraceId(), "ERROR",
                  "HTTP GET on resource 'http://0.0.0.0:9080/remote/invalid' failed: Connection refused.");
        }));

  }

  @Test
  public void testHttpAttributes() throws Exception {
    sendRequest(CORRELATION_ID, "test-remote-request", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .hasSize(3));
    DelegatedLoggingSpanTestExporter.Span head = DelegatedLoggingSpanTestExporter.spanQueue.peek();
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind")
            .containsOnly("GET /test-remote-request", "SERVER"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .hasSizeGreaterThanOrEqualTo(13)
        .containsEntry("mule.app.flow.name", "mule-opentelemetry-app-requester-remote")
        .containsKey("mule.serverId")
        .containsEntry("url.scheme", "http")
        .containsEntry("http.request.method", "GET")
        .containsEntry("http.route", "/test-remote-request")
        .containsEntry("mule.app.flow.source.name", "listener")
        .containsEntry("mule.app.flow.source.namespace", "http")
        .containsEntry("mule.app.flow.source.configRef", "HTTP_Listener_config")
        .containsKey("mule.correlationId")
        .containsKey("user_agent.original");
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind")
            .containsOnly("/test/remote/target", "CLIENT"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .containsEntry("mule.app.processor.name", "request")
        .containsEntry("mule.app.processor.namespace", "http")
        .containsEntry("mule.app.processor.docName", "Request")
        .containsEntry("http.response.status_code", 200L)
        .containsEntry("http.response.header.content-length", "18")
        .containsEntry("mule.app.processor.configRef", "SELF_HTTP_Request_configuration")
        .containsEntry("http.request.method", "GET")
        .containsEntry("http.route", "/test/remote/target");
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    System.setProperty("SELF_HTTP_Request_configuration.otel.peer.service", "service_prop_name");
    System.setProperty("SELF_HTTP_Request_configuration.otel.mule.serverId", "test-server-id");
    System.setProperty("HTTP_Listener_config.otel.key.from.sysprop", "value_from_sysprop");
    super.doSetUpBeforeMuleContextCreation();
  }

  @Test
  public void testConfigPropertiesFromSystem() throws Exception {
    // Requires "SELF_HTTP_Request_configuration.otel.peer.service" property set to
    // "service_prop_name" before crating mule context.
    // doSetUpBeforeMuleContextCreation() method does it
    sendRequest(CORRELATION_ID, "test-remote-request", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .hasSize(3));
    DelegatedLoggingSpanTestExporter.Span head = DelegatedLoggingSpanTestExporter.spanQueue.peek();
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http request")
            .extracting("spanKind")
            .isEqualTo("CLIENT"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .as("System set property for http request").containsEntry("peer.service", "service_prop_name")
        .as("System set property overriding mule serverId tag")
        .containsEntry("mule.serverid", "test-server-id");
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for http:listener flow")
            .extracting("spanName", "spanKind")
            .containsOnly("GET /test-remote-request", "SERVER"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .as("System set property for http listener").containsEntry("key.from.sysprop", "value_from_sysprop");
  }

  @Test
  public void testServer400Response() throws Exception {
    sendRequest(CORRELATION_ID, "/test/error/400", 400);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(1)
        .element(0).as("Span for http:listener flow")
        .extracting("spanName", "spanKind")
        .containsOnly("GET /test/error/400", "SERVER"));
  }

  @Test
  public void testRequestSpanWithoutBasePath() throws Exception {
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-2-private-Flow-requester-error"));
    assertThat(exception)
        .isNotNull()
        .hasMessage("HTTP GET on resource 'http://0.0.0.0:9080/remote/invalid' failed: Connection refused.");

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
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

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .extracting("spanName", "spanKind")
              .containsExactly("/api/remote/invalid", "CLIENT");
        }));
  }

  @Test
  public void testHTTPSpanNames_withExpression() throws Exception {
    Throwable throwable = catchThrowable(() -> flowRunner("flow-call-remote-with-expression")
        .withVariable("resourcePath", "/expression/secrets")
        .run());
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(2)
        .element(0).as("Span for http:request flow")
        .extracting("spanName", "spanKind")
        .containsOnly("/expression/secrets", "CLIENT"));
    DelegatedLoggingSpanTestExporter.Span client = getSpan("CLIENT", "/expression/secrets");
    assertThat(client).isNotNull()
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .containsEntry("http.route", "/expression/secrets");

  }

  @Test
  public void testHTTPAttributes_Hostname_withExpression() throws Exception {
    // GitHub# issues/257
    Throwable throwable = catchThrowable(() -> flowRunner("flow-call-remote-with-host-static-expression")
        .withVariable("resourcePath", "/expression/secrets")
        .run());
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(2)
        .element(0).as("Span for http:request flow")
        .extracting("spanName", "spanKind")
        .containsOnly("/expression/secrets", "CLIENT"));
    DelegatedLoggingSpanTestExporter.Span client = getSpan("CLIENT", "/expression/secrets");
    assertThat(client).isNotNull()
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .containsEntry("http.route", "/expression/secrets")
        .containsEntry("server.address", "0.0.0.0");
  }

  @Test
  public void testHTTPAttributes_Hostname_withExpressionJSONStream() throws Exception {
    // GitHub# issues/257
    Throwable throwable = catchThrowable(() -> flowRunner("flow-call-remote-with-host-dynamic-json-expression")
        .withVariable("resourcePath", "/expression/secrets")
        .run());
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(2)
        .element(0).as("Span for http:request flow")
        .extracting("spanName", "spanKind")
        .containsOnly("/expression/secrets", "CLIENT"));
    DelegatedLoggingSpanTestExporter.Span client = getSpan("CLIENT", "/expression/secrets");
    assertThat(client).isNotNull()
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .containsEntry("http.route", "/expression/secrets")
        .containsEntry("server.address", "localhost");
  }

}
