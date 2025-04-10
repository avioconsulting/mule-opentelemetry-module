package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import com.avioconsulting.mule.opentelemetry.test.util.TestLoggerHandler;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@ArtifactClassLoaderRunnerConfig(exportPluginClasses = { OpenTelemetryConnection.class,
    DelegatedLoggingSpanTestExporterProvider.class }, applicationSharedRuntimeLibs = {
        "org.apache.derby:derby" })
public abstract class AbstractMuleArtifactTraceTest extends MuleArtifactFunctionalTestCase {

  public static final String CORRELATION_ID = UUID.randomUUID().toString();
  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  protected static final java.util.Queue<CoreEvent> CAPTURED = new ConcurrentLinkedDeque<>();

  protected static DelegatedLoggingSpanTestExporter.Span getSpan(String spanKind, String spanName) {
    return DelegatedLoggingSpanTestExporter.spanQueue
        .stream()
        .filter(s -> s.getSpanKind().equals(spanKind) && s.getSpanName().equals(spanName))
        .findFirst().get();
  }

  @Before
  public void beforeTest() {
    Awaitility.reset();
    Awaitility.setDefaultPollDelay(100, MILLISECONDS);
    Awaitility.setDefaultPollInterval(2, SECONDS);
    Awaitility.setDefaultTimeout(10, SECONDS);

  }

  @After
  public void clearSpansQueue() {
    Awaitility.await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue).as(
        "Cleaning span queue with @Before. If It is expected to be empty, override clearSpansQueue from abstract test.")
        .isNotEmpty());
    DelegatedLoggingSpanTestExporter.spanQueue.clear();
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    OpenTelemetryConnection.resetForTest();
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty(TEST_TIMEOUT_SYSTEM_PROPERTY, "120_000_000");
    System.setProperty("http.host", "localhost");
    // Reduce the time between batch export. Speeds up the completion.
    System.setProperty("otel.bsp.schedule.delay", "100");
  }

  protected void withOtelEndpoint() {
    System.setProperty("otel.traces.exporter", "otlp");
    System.setProperty("otel.exporter.otlp.endpoint", "http://localhost:55681/v1");
    System.setProperty(
        "otel.exporter.otlp.traces.endpoint", "http://localhost:55681/v1/traces");
    System.setProperty("otel.exporter.otlp.protocol", "http/protobuf");
  }

  @NotNull
  protected Map<Object, Set<String>> groupSpanByParent() {
    // Find the root span
    DelegatedLoggingSpanTestExporter.Span root = spanQueue.stream()
        .filter(span -> span.getParentSpanContext().getSpanId().equals("0000000000000000")).findFirst().get();

    // Create a lookup of span id and name
    Map<String, String> idNameMap = spanQueue.stream().collect(Collectors.toMap(
        DelegatedLoggingSpanTestExporter.Span::getSpanId, DelegatedLoggingSpanTestExporter.Span::getSpanName));

    return spanQueue.stream()
        .collect(Collectors.groupingBy(
            span -> idNameMap.getOrDefault(span.getParentSpanContext().getSpanId(), root.getSpanName()),
            Collectors.mapping(DelegatedLoggingSpanTestExporter.Span::getSpanName, Collectors.toSet())));
  }

  /**
   * Gets a {@link Logger} used by
   * `io.opentelemetry.exporter.logging.LoggingSpanExporter`.
   * Registers a {@link TestLoggerHandler} as a log handler for log entry
   * extraction during tests.
   *
   * @return {@link TestLoggerHandler}
   */
  protected TestLoggerHandler getTestLoggerHandler() {
    Logger logger = Logger.getLogger("io.opentelemetry.exporter.logging.LoggingSpanExporter");
    TestLoggerHandler loggerHandler = new TestLoggerHandler();
    logger.addHandler(loggerHandler);
    return loggerHandler;
  }

  protected CoreEvent getCapturedEvent(long timeout, String failureDescription) {
    AtomicReference<CoreEvent> value = new AtomicReference<>();
    new PollingProber(timeout, 100)
        .check(
            new JUnitLambdaProbe(
                () -> {
                  synchronized (CAPTURED) {
                    CoreEvent capturedEvent = CAPTURED.poll();
                    if (capturedEvent != null) {
                      value.set(capturedEvent);
                      return true;
                    }
                    return false;
                  }
                },
                failureDescription));

    return value.get();
  }

  protected void sendRequest(String correlationId, String path, int expectedStatus)
      throws IOException, URISyntaxException {
    sendRequest(correlationId, path, expectedStatus, Collections.emptyMap());
  }

  protected void sendRequest(String method, String correlationId, String path, int expectedStatus, HttpEntity body)
      throws IOException, URISyntaxException {
    sendRequest(method, correlationId, path, expectedStatus, Collections.emptyMap(), Collections.emptyMap(), body);
  }

  protected void sendRequest(String correlationId, String path, int expectedStatus, Map<String, String> headers)
      throws IOException, URISyntaxException {
    sendRequest(correlationId, path, expectedStatus, headers, Collections.emptyMap());
  }

  protected void sendRequest(String correlationId, String path, int expectedStatus, Map<String, String> headers,
      Map<String, String> queryParams)
      throws IOException, URISyntaxException {
    sendRequest("get", correlationId, path, expectedStatus, headers, queryParams, null);
  }

  protected void sendRequest(String method, String correlationId, String path, int expectedStatus,
      Map<String, String> headers,
      Map<String, String> queryParams, HttpEntity body) throws URISyntaxException, IOException {
    HttpUriRequest request;
    URIBuilder uriBuilder = new URIBuilder(String.format("http://localhost:%s/" + path, serverPort.getValue()));
    queryParams.forEach(uriBuilder::addParameter);
    URI uri = uriBuilder.build();
    if ("post".equals(method)) {
      request = new HttpPost(uri);
      if (body != null)
        ((HttpPost) request).setEntity(body);
    } else {
      request = new HttpGet(uri);
    }
    request.addHeader("X-CORRELATION-ID", correlationId);
    headers.forEach(request::addHeader);
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      try (CloseableHttpResponse response = httpClient.execute(request)) {
        if (expectedStatus != -1) {
          assertThat(response.getStatusLine().getStatusCode()).isEqualTo(expectedStatus);
        }
      }
    }

  }
}
