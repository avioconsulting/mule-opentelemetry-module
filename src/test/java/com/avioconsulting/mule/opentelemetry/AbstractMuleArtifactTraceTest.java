package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import com.avioconsulting.mule.opentelemetry.test.util.TestLoggerHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.awaitility.Awaitility;
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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@ArtifactClassLoaderRunnerConfig(exportPluginClasses = {
    DelegatedLoggingSpanExporterProvider.class }, applicationSharedRuntimeLibs = {
        "org.apache.derby:derby" })
public abstract class AbstractMuleArtifactTraceTest extends MuleArtifactFunctionalTestCase {

  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  protected static final java.util.Queue<CoreEvent> CAPTURED = new ConcurrentLinkedDeque<>();

  @Before
  public void beforeTest() {
    Awaitility.reset();
    Awaitility.setDefaultPollDelay(100, MILLISECONDS);
    Awaitility.setDefaultPollInterval(2, SECONDS);
    Awaitility.setDefaultTimeout(10, SECONDS);
  }

  @After
  public void clearSpansQueue() {
    Awaitility.await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue).isNotEmpty());
    DelegatedLoggingSpanExporter.spanQueue.clear();
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty(TEST_TIMEOUT_SYSTEM_PROPERTY, "120_000_000");
  }

  protected void withOtelEndpoint() {
    System.setProperty("otel.traces.exporter", "otlp");
    System.setProperty("otel.exporter.otlp.endpoint", "http://localhost:55681/v1");
    System.setProperty(
        "otel.exporter.otlp.traces.endpoint", "http://localhost:55681/v1/traces");
    System.setProperty("otel.exporter.otlp.protocol", "http/protobuf");
  }

  /**
   * Gets a @{@link Logger} used by
   * `io.opentelemetry.exporter.logging.LoggingSpanExporter`.
   * Registers a @{@link TestLoggerHandler} as a log handler for log entry
   * extraction during tests.
   * 
   * @return @{@link TestLoggerHandler}
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
      throws IOException {
    sendRequest(correlationId, path, expectedStatus, Collections.emptyMap());
  }

  protected void sendRequest(String correlationId, String path, int expectedStatus, Map<String, String> headers)
      throws IOException {
    HttpGet getRequest = new HttpGet(String.format("http://localhost:%s/" + path, serverPort.getValue()));
    getRequest.addHeader("X-CORRELATION-ID", correlationId);
    headers.forEach(getRequest::addHeader);
    // getRequest.addHeader();
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(expectedStatus);
      }
    }
  }
}
