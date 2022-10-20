package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import junitparams.JUnitParamsRunner;
import org.junit.*;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.test.runner.RunnerDelegateTo;

import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunnerDelegateTo(JUnitParamsRunner.class)
public class MuleOpenTelemetryAnypointMQTest extends AbstractMuleArtifactTraceTest {

  /**
   * Use static {@link ClassRule} for port. Avoids unpredictable port usage
   * across tests.
   * Making it a {@link Rule} may cause failure in assertions related to port
   * usage when all tests run.
   */
  @ClassRule
  public static DynamicPort amqPort = new DynamicPort("anypoint.mq.port");

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(amqPort.getNumber()));

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("Anypoint_MQ_Config.otel.mq.system.fromprop", "AnypointMQ_Sys");
  }

  @Before
  public void setupMQ() {
    wireMockRule.stubFor(get(urlEqualTo(
        "/api/v1/organizations/f2ea2cb4-c600-4bb5-88e8-e952ff5591ee/environments/c06ef9b7-19c0-4e87-add9-60ed58b20aad/destinations/otel-test-queue-1/messages?batchSize=10&pollingTime=20000&lockTtl=60000"))
            .willReturn(okJson(
                "[ {\n  \"properties\" : {\n    \"traceparent\" : \"00-56e7765c3b2a673b0394a9fe7b2b7253-7658fdea51c1ec71-01\",\n    \"TRACE_TRANSACTION_ID\" : \"19fe6800-0eb5-4da7-97b4-8c560b98a31b\",\n    \"contentType\" : \"application/json; charset=UTF-8\",\n    \"MULE_ENCODING\" : \"UTF-8\"\n  },\n  \"headers\" : {\n    \"messageId\" : \"65894714-e890-4054-9696-7f6614a048e9\",\n    \"lockId\" : \"a\",\n    \"ttl\" : \"120000\",\n    \"created\" : \"Fri, 4 Mar 2022 03:53:34 GMT\",\n    \"deliveryCount\" : \"1\"\n  },\n  \"body\" : \"{\\n  \\\"id\\\": 1\\n}\"\n} ]")
                    .withHeader("content-type", "application/json")));
    wireMockRule.stubFor(put(urlEqualTo(
        "/api/v1/organizations/f2ea2cb4-c600-4bb5-88e8-e952ff5591ee/environments/c06ef9b7-19c0-4e87-add9-60ed58b20aad/destinations/otel-test-queue-1/messages/65894714-e890-4054-9696-7f6614a048e9"))
            .withRequestBody(equalToJson(
                "{\"body\":\"{\\n  \\\"id\\\": 1\\n}\",\"headers\":{\"messageId\":\"65894714-e890-4054-9696-7f6614a048e9\"},\"properties\":{}}",
                true, true))
            .willReturn(aResponse().withStatus(201).withBody(
                "{\n  \"messageId\" : \"65894714-e890-4054-9696-7f6614a048e9\",\n  \"status\" : \"successful\",\n  \"statusMessage\" : \"Send operation successful\"\n}")
                .withHeader("content-type", "application/json")));
  }

  @After
  public void stopWireMock() {
    wireMockRule.stop();
  }

  @Override
  protected String getConfigFile() {
    return "anypoint-mq-flows.xml";
  }

  private void assertSpan(DelegatedLoggingSpanExporterProvider.Span span, String docName, String spanKind) {

    assertThat(span.getAttributes())
        .containsEntry("messaging.system", "anypointmq")
        .containsEntry("messaging.destination_kind", "queue")
        .containsEntry("messaging.url", "http://localhost:" + wireMockRule.port() + "/api/v1")
        .containsEntry("messaging.consumer_id", "2327057f85ab4340b2f27c7b1b20cb07")
        .containsEntry("messaging.destination", "otel-test-queue-1")
        .containsEntry("messaging.protocol", "http")
        .as("System set property").containsEntry("mq.system.fromprop", "AnypointMQ_Sys");
    ;
    if (spanKind.equalsIgnoreCase("PRODUCER")) {
      assertThat(span)
          .extracting("spanName", "spanKind", "spanStatus")
          .containsOnly("otel-test-queue-1 send", spanKind, "OK");
    }
    if (spanKind.equalsIgnoreCase("CONSUMER")
        && docName.equalsIgnoreCase("Subscriber")) {
      assertThat(span.getAttributes())
          .containsEntry("mule.app.flow.source.name", docName.toLowerCase())
          .containsEntry("mule.app.flow.source.namespace", "anypoint-mq")
          .containsEntry("mule.app.flow.source.configRef", "Anypoint_MQ_Config");
    } else {
      assertThat(span.getAttributes())
          .containsEntry("mule.app.processor.configRef", "Anypoint_MQ_Config")
          .containsEntry("mule.app.processor.name", docName.toLowerCase())
          .containsEntry("mule.app.processor.docName", docName)
          .containsEntry("mule.app.processor.namespace", "anypoint-mq");
    }

  }

  @Test
  public void testValid_AnypointMQ_PublishTrace() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/amq/publish", 200,
        Collections.singletonMap("traceparent", "00-56e7765c3b2a673b0394a9fe7b2b7253-7658fdea51c1ec71-01"));

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSizeGreaterThanOrEqualTo(3));
    assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .anySatisfy(span -> assertSpan(span, "Publish", "PRODUCER"))
        .anySatisfy(span -> assertSpan(span, "Publish", "SERVER"));
  }

  @Test
  public void testValid_AnypointMQ_ConsumeTrace() throws Exception {
    wireMockRule.stubFor(get(urlEqualTo(
        "/api/v1/organizations/f2ea2cb4-c600-4bb5-88e8-e952ff5591ee/environments/c06ef9b7-19c0-4e87-add9-60ed58b20aad/destinations/otel-test-queue-1/messages"))
            .willReturn(okJson(
                "[ {\n  \"properties\" : {\n    \"traceparent\" : \"00-56e7765c3b2a673b0394a9fe7b2b7253-7658fdea51c1ec71-01\",\n    \"TRACE_TRANSACTION_ID\" : \"19fe6800-0eb5-4da7-97b4-8c560b98a31b\",\n    \"contentType\" : \"application/json; charset=UTF-8\",\n    \"MULE_ENCODING\" : \"UTF-8\"\n  },\n  \"headers\" : {\n    \"messageId\" : \"65894714-e890-4054-9696-7f6614a048e9\",\n    \"lockId\" : \"a\",\n    \"ttl\" : \"120000\",\n    \"created\" : \"Fri, 4 Mar 2022 03:53:34 GMT\",\n    \"deliveryCount\" : \"1\"\n  },\n  \"body\" : \"{\\n  \\\"id\\\": 1\\n}\"\n} ]")
                    .withHeader("content-type", "application/json")));
    runFlow("anypoint-mq-consume-operation");
    await().untilAsserted(() -> {
      assertThat(DelegatedLoggingSpanExporter.spanQueue)
          .hasSizeGreaterThanOrEqualTo(2)
          .anySatisfy(span -> assertSpan(span, "Consume", "CONSUMER"));
    });
  }

  @Test
  public void testSubscriberTrace() throws Exception {
    runFlow("anypoint-mq-flowsFlow");
    await().untilAsserted(() -> {
      assertThat(DelegatedLoggingSpanExporter.spanQueue)
          .hasSizeGreaterThanOrEqualTo(2)
          .filteredOn(span -> span.getSpanKind().equals("CONSUMER"))
          .allSatisfy(span -> assertSpan(span, "Subscriber", "CONSUMER"));
    });
  }
}
