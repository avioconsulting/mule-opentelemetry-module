import com.avioconsulting.mule.opentelemetry.AbstractMuleArtifactTraceTest;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.extension.http.api.HttpResponseAttributes;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryDynamicHttpTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-dynamic-http.xml";
  }

  @Test
  public void resolvesDynamicHostAndPortWithExplicitMethod() throws Exception {
    assertSuccessfulRequestAndClientSpan("dynamic-http-explicit-method");
  }

  @Test
  public void resolvesDynamicHostAndPortWithDefaultMethod() throws Exception {
    assertSuccessfulRequestAndClientSpan("dynamic-http-default-method");
  }

  @Test
  public void resolvesDynamicHostWithDefaultMethod() throws Exception {
    assertSuccessfulRequestAndClientSpan("dynamic-http-host-default-method");
  }

  @Test
  public void resolvesDynamicHostAndPortWithExplicitUrl() throws Exception {
    assertSuccessfulRequestAndClientSpan("dynamic-http-explicit-url", null);
  }

  private void assertSuccessfulRequestAndClientSpan(String flow) throws Exception {
    assertSuccessfulRequestAndClientSpan(flow, "/dynamic-http-target");
  }

  private void assertSuccessfulRequestAndClientSpan(String flow, String path) throws Exception {
    CoreEvent response = flowRunner(flow)
        .withVariable("dynamicConfig", Collections.singletonMap("port", Integer.valueOf(serverPort.getValue())))
        .withVariable("region", "host")
        .run();
    // The real requester must succeed before testing its telemetry.
    assertThat(((HttpResponseAttributes) response.getMessage().getAttributes().getValue()).getStatusCode())
        .isEqualTo(200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span.getSpanKind()).isEqualTo("CLIENT");
          if (path != null) {
            assertThat(span.getSpanName()).isEqualTo(path);
          }
          assertThat(span.getAttributes())
              .containsEntry("server.address", "localhost")
              .containsEntry("server.port", Long.valueOf(serverPort.getValue()));
        }));
  }
}
