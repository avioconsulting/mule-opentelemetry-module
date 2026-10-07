package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.junit.Test;
import org.junit.After;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter;
import static org.assertj.core.api.Assertions.assertThat;

@ArtifactClassLoaderRunnerConfig(exportPluginClasses = { OpenTelemetryExtensionConfiguration.class,
    OtlpExporter.class, ExporterAuthentication.class, BearerTokenAuth.class, ApiKeyAuth.class, OAuth2Auth.class })
public class ExporterAuthenticationXmlTest extends MuleArtifactFunctionalTestCase {
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

  private ExporterAuthentication authentication(String name) {
    org.mule.runtime.extension.api.runtime.config.ConfigurationProvider provider = (org.mule.runtime.extension.api.runtime.config.ConfigurationProvider) registry
        .lookupByName(name).get();
    OpenTelemetryExtensionConfiguration config = (OpenTelemetryExtensionConfiguration) provider.get(null)
        .getValue();
    return ((OtlpExporter) config.getExporterConfiguration().getExporter()).getAuthentication();
  }
}
