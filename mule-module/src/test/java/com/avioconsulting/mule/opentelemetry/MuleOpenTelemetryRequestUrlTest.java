package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.ClassRule;
import org.junit.Test;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryRequestUrlTest extends AbstractMuleArtifactTraceTest {

  // Mule reuses the application across methods; its listener port must stay
  // fixed.
  @ClassRule
  public static DynamicPort targetPort = new DynamicPort("request.url.target.port");

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-request-url.xml";
  }

  @Test
  public void loopbackUrlReallyOverridesConfiguredDestination() throws Exception {
    CoreEvent response = flowRunner("request-url-dynamic")
        .withVariable("targetUrl", "http://127.0.0.1:" + targetPort.getValue() + "/actual/destination-proof")
        .run();
    assertSuccessfulDestination(response, "/actual/destination-proof");
  }

  @Test
  public void literalUrlReachesActualDestinationInsteadOfConfiguredConnection() throws Exception {
    CoreEvent response = flowRunner("request-url-literal").run();
    assertSuccessfulDestination(response, "/actual/literal");
    assertClientUrl("/actual/literal", "literal-secret");
  }

  @Test
  public void dynamicUrlIsResolvedIndependentlyForConsecutiveEvents() throws Exception {
    for (String id : new String[] { "first", "second" }) {
      String path = "/actual/" + id;
      CoreEvent response = flowRunner("request-url-dynamic")
          .withVariable("targetUrl",
              "http://127.0.0.1:" + targetPort.getValue() + path + "?token=" + id + "-secret")
          .run();
      assertSuccessfulDestination(response, path);
    }
    assertClientUrl("/actual/first", "first-secret");
    assertClientUrl("/actual/second", "second-secret");
    List<DelegatedLoggingSpanTestExporter.Span> clients = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> "CLIENT".equals(span.getSpanKind())).collect(Collectors.toList());
    assertThat(clients).hasSize(2);
  }

  private void assertSuccessfulDestination(CoreEvent response, String path) {
    assertThat(((HttpResponseAttributes) response.getMessage().getAttributes().getValue()).getStatusCode())
        .isEqualTo(200);
    // The target echoes its request path, proving that URL (not
    // basePath/connection) was used.
    assertThat(response.getMessage().getPayload().getValue().toString()).isEqualTo(path);
  }

  private void assertClientUrl(String path, String secret) {
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue).anySatisfy(span -> {
      assertThat(span.getSpanKind()).isEqualTo("CLIENT");
      assertThat(span.getSpanName()).isEqualTo("GET");
      assertThat(span.getAttributes()).containsEntry("server.address", "127.0.0.1")
          .containsEntry("server.port", Long.valueOf(targetPort.getValue()))
          .containsEntry("url.scheme", "http").containsEntry("url.path", path)
          .containsEntry("http.request.method", "GET").doesNotContainKey("http.route");
      String query = (String) span.getAttributes().get("url.query");
      assertThat(query).startsWith("token=").doesNotContain(secret);
      assertThat(span.getAttributes().get("url.full"))
          .isEqualTo("http://127.0.0.1:" + targetPort.getValue() + path + "?" + query);
      assertThat(span.getAttributes().toString()).doesNotContain(secret, "configured.invalid",
          "#[vars.targetUrl]");
    }));
  }
}
