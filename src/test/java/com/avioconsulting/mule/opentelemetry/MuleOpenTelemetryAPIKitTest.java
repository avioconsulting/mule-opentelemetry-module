package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryAPIKitTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "apikit-order-exp.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    System.setProperty("mule.otel.http.root.span.route.path", "true");
    super.doSetUpBeforeMuleContextCreation();
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    System.clearProperty("mule.otel.http.root.span.route.path");
    super.doTearDownAfterMuleContextDispose();
  }

  @Test
  public void getAPIKitOrders() throws Exception {
    sendRequest(CORRELATION_ID, "/api/orders/1234", 200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(8)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("GET /api/orders/{orderId}", "SERVER", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener apikit flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("get:\\orders\\(orderId):order-exp-config", "SERVER", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for apikit router component")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("router:router order-exp-config", "INTERNAL", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request target flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("/test", "CLIENT", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener target flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("GET /test", "SERVER", "UNSET");
        }));
  }

  @Test
  public void getAPIKitOrders_404Error() throws Exception {
    sendRequest(CORRELATION_ID, "/api/something/1234", 404);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(4)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .describedAs(
                  "HTTP Source span not reaching to APIKit Flow and thus have the default HTTP Route path from listener as Span name")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("GET /api/*", "SERVER", "UNSET");
        }));
  }

  @Test
  public void postAPIKitOrders() throws Exception {
    StringEntity stringEntity = new StringEntity(
        "{\"customerId\": \"d3988a7a-d36c-4483-b9d4-8fa5c3f46255\",\"orderItems\": " +
            "[{\"productId\": 1,\"quantity\": 3}]," +
            "\"shipmentAddressId\": \"4fb029b5-7b1b-4f26-b2b7-cd81879db669\"}",
        ContentType.APPLICATION_JSON);
    sendRequest("post", CORRELATION_ID, "/api/orders", 201, stringEntity);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(4)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener source flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("POST /api/orders", "SERVER", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:listener apikit flow")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("post:\\orders:application\\json:order-exp-config", "SERVER", "UNSET");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for apikit router component")
              .extracting("spanName", "spanKind", "spanStatus")
              .containsOnly("router:router order-exp-config", "INTERNAL", "UNSET");
        }));
  }
}
