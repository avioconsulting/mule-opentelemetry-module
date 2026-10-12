import com.avioconsulting.mule.opentelemetry.AbstractMuleArtifactTraceTest;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.Rule;
import org.junit.Test;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryDynamicHostHttpTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public DynamicPort targetPort = new DynamicPort("dynamic.host.http.port");

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-dynamic-host-http.xml";
  }

  @Test
  public void resolvesDynamicHostWithExplicitUrl() throws Exception {
    CoreEvent response = flowRunner("dynamic-host-http-explicit-url")
        .withVariable("region", "host")
        .withVariable("targetPort", Integer.valueOf(targetPort.getValue()))
        .run();
    assertThat(((HttpResponseAttributes) response.getMessage().getAttributes().getValue()).getStatusCode())
        .isEqualTo(200);
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span.getSpanKind()).isEqualTo("CLIENT");
          assertThat(span.getAttributes())
              .containsEntry("server.address", "localhost")
              .containsEntry("server.port", Long.valueOf(targetPort.getValue()));
        }));
  }
}
