package com.avioconsulting.mule.opentelemetry.internal.connection;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

public class ConfigurationSanitizerTest {
  @Test
  public void redactsAllPropertyValuesIncludingHeaderAndTlsSecrets() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.exporter.otlp.headers", "Authorization=dummy-secret");
    properties.put("otel.exporter.otlp.client.key", "dummy-key");
    properties.put("otel.exporter.otlp.endpoint", "https://user:dummy-secret@localhost");
    properties.put("custom-sensitive-name", "dummy-secret");
    assertThat(ConfigurationSanitizer.sanitize(properties).toString()).doesNotContain("dummy-secret", "dummy-key");
    assertThat(properties.get("otel.exporter.otlp.headers")).contains("dummy-secret");
  }
}
