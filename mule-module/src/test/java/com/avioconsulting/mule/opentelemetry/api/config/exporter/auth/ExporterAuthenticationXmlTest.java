package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.junit.Test;
import org.junit.After;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter;
import static org.assertj.core.api.Assertions.assertThat;

public class ExporterAuthenticationXmlTest extends com.avioconsulting.mule.opentelemetry.AbstractMuleArtifactTraceTest {
  @Override
  public void clearSpansQueue() {
    /* This configuration-only fixture does not generate spans. */ }

  private String previousDisabled;

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    previousDisabled = System.getProperty("otel.sdk.disabled");
    System.setProperty("otel.sdk.disabled", "true");
    super.doSetUpBeforeMuleContextCreation();
  }

  @After
  public void restoreSdkSetting() {
    if (previousDisabled == null) {
      System.clearProperty("otel.sdk.disabled");
    } else {
      System.setProperty("otel.sdk.disabled", previousDisabled);
    }
  }

  @Override
  protected String getConfigFile() {
    return "exporter-authentication.xml";
  }

  @Test
  public void loadsAllAuthenticationSubtypesWithIndependentHeaders() {
    assertThat(authentication("bearer")).isInstanceOf(BearerTokenAuth.class);
    assertThat(authentication("apiKey")).isInstanceOf(ApiKeyAuth.class);
    assertThat(authentication("oauth")).isInstanceOf(OAuth2Auth.class);
  }

  @Test
  public void loadsRefreshBeforeExpirySeconds() {
    assertThat(((OAuth2Auth) authentication("oauth")).getRefreshBeforeExpirySeconds()).isEqualTo(90);
  }

  private ExporterAuthentication authentication(String name) {
    return exporter(name).getAuthentication();
  }

  @Test
  public void loadsSignalEndpointsForHttpAndGrpcWithAuthentication() {
    OtlpExporter http = exporter("bearer");
    assertThat(http.getCollectorEndpoint()).isNull();
    assertThat(http.getTracesEndpoint())
        .isEqualTo("https://localhost:4318/custom/traces?api-version=1&tenant=example");
    assertThat(http.getMetricsEndpoint()).isEqualTo("https://localhost:4318/custom/metrics");
    assertThat(http.getLogsEndpoint()).isEqualTo("https://localhost:4318/custom/logs");
    OtlpExporter grpc = exporter("apiKey");
    assertThat(grpc.getCollectorEndpoint()).isEqualTo("https://localhost:4317");
    assertThat(grpc.getTracesEndpoint()).isEqualTo("https://localhost:5317");
    assertThat(grpc.getMetricsEndpoint()).isEqualTo("https://localhost:6317");
    assertThat(grpc.getLogsEndpoint()).isEqualTo("https://localhost:7317");
  }

  private OtlpExporter exporter(String name) {
    org.mule.runtime.extension.api.runtime.config.ConfigurationProvider provider = (org.mule.runtime.extension.api.runtime.config.ConfigurationProvider) registry
        .lookupByName(name).get();
    OpenTelemetryExtensionConfiguration config = (OpenTelemetryExtensionConfiguration) provider.get(null)
        .getValue();
    return (OtlpExporter) config.getExporterConfiguration().getExporter();
  }
}
