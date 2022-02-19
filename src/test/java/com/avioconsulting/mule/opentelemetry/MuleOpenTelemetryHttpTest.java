package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.test.util.Span;
import com.avioconsulting.mule.opentelemetry.test.util.TestLoggerHandler;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryHttpTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Test
  public void testValidHttpTracing() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    await().untilAsserted(() -> assertThat(Span.fromStrings(loggerHandler.getCapturedLogs()))
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind")
        .containsOnly("'/test'", "SERVER"));
  }

  @Test
  public void testTraceContextExtraction() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "/test/propagation/source", 200);
    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .hasSize(3)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/test/propagation/source'", "SERVER", spans.get(0).getTraceId());
        })
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/test/propagation/target'", "CLIENT", spans.get(0).getTraceId());
        })
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener target flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/test/propagation/target'", "SERVER", spans.get(0).getTraceId());
        }));
  }

  @Test
  public void testInvalidHttpRequest() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "test-invalid-request", 500);
    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .hasSize(2)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/test-invalid-request'", "SERVER", spans.get(0).getTraceId());
        })
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/remote/invalid'", "CLIENT", spans.get(0).getTraceId());
        }));

  }

  @Test
  public void testHttpAttributes() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "test-invalid-request", 500);
    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .hasSize(2)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener flow")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/test-invalid-request'", "SERVER", spans.get(0).getTraceId());
          assertThat(span.getAttributes().getDataMap())
              .hasSize(9)
              .containsEntry("mule.app.flow.name", "mule-opentelemetry-app-2Flow-requester-error")
              .containsKey("mule.serverId")
              .containsEntry("http.scheme", "http")
              .containsEntry("http.method", "GET")
              .containsEntry("http.route", "/test-invalid-request")
              .containsKey("http.user_agent")
              .hasEntrySatisfying("http.host", value -> assertThat(value).startsWith("localhost:"))
              .containsEntry("http.flavor", "1.1");

        })
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request")
              .extracting("spanName", "spanKind", "traceId")
              .containsOnly("'/remote/invalid'", "CLIENT", spans.get(0).getTraceId());
          assertThat(span.getAttributes().getDataMap())
              .hasSize(10)
              .containsEntry("mule.app.processor.name", "request")
              .containsEntry("mule.app.processor.namespace", "http")
              .containsEntry("mule.app.processor.docName", "Request")
              .containsEntry("http.host", "0.0.0.0:9080")
              .containsEntry("http.scheme", "http")
              .containsEntry("net.peer.name", "0.0.0.0")
              .containsEntry("mule.app.processor.configRef", "INVALID_HTTP_Request_configuration")
              .containsEntry("http.method", "GET")
              .containsEntry("http.route", "/remote/invalid")
              .containsEntry("net.peer.port", "9080");
        }));

  }

  @Test
  public void testServer400Response() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "/test/error/400", 400);
    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .hasSize(1)
        .element(0).as("Span for http:listener flow")
        .extracting("spanName", "spanKind")
        .containsOnly("'/test/error/400'", "SERVER"));
  }

  @Test
  public void testRequestSpanWithoutBasePath() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-2-private-Flow-requester-error"));
    assertThat(exception)
        .isNotNull()
        .hasMessage("HTTP GET on resource 'http://0.0.0.0:9080/remote/invalid' failed: Connection refused.");

    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .anySatisfy(span -> {
          assertThat(span)
              .extracting("spanName", "spanKind")
              .containsExactly("'/remote/invalid'", "CLIENT");
        }));
  }

  @Test
  @Ignore(value = "Individual run of this test succeeds but when run in suite, it fails with error 'BeanFactory not initialized or already closed - call 'refresh' before accessing beans via the ApplicationContext'. TODO: Find root cause and enable test.")
  public void testRequestSpanWithBasePath() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-2-private-Flow-requester_basepath"));
    assertThat(exception)
        .isNotNull()
        .hasMessage(
            "HTTP GET on resource 'http://0.0.0.0:9085/api/remote/invalid' failed: Connection refused.");

    List<Span> spans = Span.fromStrings(loggerHandler.getCapturedLogs());
    await().untilAsserted(() -> assertThat(spans)
        .anySatisfy(span -> {
          assertThat(span)
              .extracting("spanName", "spanKind")
              .containsExactly("'/api/remote/invalid'", "CLIENT");
        }));
  }
}
