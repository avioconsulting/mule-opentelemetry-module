package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.BearerTokenAuth;
import com.avioconsulting.mule.opentelemetry.internal.auth.StaticAuthenticationProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.junit.Test;
import java.util.Collections;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OtlpAuthenticationCustomizerTest {
  private final OtlpAuthenticationCustomizer customizer = new OtlpAuthenticationCustomizer(
      new StaticAuthenticationProvider(new BearerTokenAuth("dummy")));

  private ConfigProperties properties() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getString("otel.exporter.otlp.endpoint")).thenReturn("https://localhost:4318");
    when(properties.getMap("otel.exporter.otlp.headers")).thenReturn(Collections.emptyMap());
    return properties;
  }

  @Test
  public void preservesMetricSelectorsMemoryModeAndStaticHeaders() {
    OtlpHttpMetricExporter original = OtlpHttpMetricExporter.builder()
        .setEndpoint("https://localhost:4318/v1/metrics")
        .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
        .setMemoryMode(MemoryMode.REUSABLE_DATA).addHeader("X-Tenant", "dummy").build();
    MetricExporter replacement = customizer.metrics(original, properties());
    try {
      assertThat(replacement.getAggregationTemporality(InstrumentType.COUNTER))
          .isEqualTo(original.getAggregationTemporality(InstrumentType.COUNTER));
      assertThat(replacement.getDefaultAggregation(InstrumentType.HISTOGRAM))
          .isEqualTo(original.getDefaultAggregation(InstrumentType.HISTOGRAM));
      assertThat(replacement.getMemoryMode()).isEqualTo(MemoryMode.REUSABLE_DATA);
      assertThat(replacement.toString()).doesNotContain("dummy");
    } finally {
      replacement.shutdown();
    }
  }

  @Test
  public void rejectsOwnedHeaderCaseInsensitivelyAndInsecureEndpoint() {
    ConfigProperties properties = properties();
    when(properties.getMap("otel.exporter.otlp.headers"))
        .thenReturn(Collections.singletonMap("aUtHoRiZaTiOn", "secret"));
    OtlpHttpMetricExporter original = OtlpHttpMetricExporter.builder().build();
    try {
      assertThatThrownBy(() -> customizer.metrics(original, properties)).hasMessageNotContaining("secret");
      when(properties.getMap("otel.exporter.otlp.headers")).thenReturn(Collections.emptyMap());
      when(properties.getString("otel.exporter.otlp.endpoint")).thenReturn("http://localhost:4318");
      assertThatThrownBy(() -> customizer.metrics(original, properties)).hasMessageContaining("secure");
    } finally {
      original.shutdown();
    }
  }

  @Test
  public void leavesUnrelatedExportersUnchanged() {
    MetricExporter unrelated = mock(MetricExporter.class);
    assertThat(customizer.metrics(unrelated, properties())).isSameAs(unrelated);
    assertThat(customizer.isUsed()).isFalse();
  }

  @Test
  public void systemOverridesCanDisableEveryAuthenticatedExporterWithoutAcquisition() {
    com.avioconsulting.mule.opentelemetry.internal.auth.ExporterAuthenticationProvider provider = mock(
        com.avioconsulting.mule.opentelemetry.internal.auth.ExporterAuthenticationProvider.class);
    OtlpAuthenticationCustomizer callbacks = new OtlpAuthenticationCustomizer(provider);
    java.util.Map<String, String> defaults = new java.util.HashMap<>();
    defaults.put("otel.traces.exporter", "otlp");
    defaults.put("otel.metrics.exporter", "otlp");
    defaults.put("otel.logs.exporter", "otlp");
    defaults.put("otel.exporter.otlp.endpoint", "https://localhost:4317");
    java.util.Map<String, String> previous = new java.util.HashMap<>();
    for (String key : new String[] { "otel.traces.exporter", "otel.metrics.exporter", "otel.logs.exporter" }) {
      previous.put(key, System.getProperty(key));
      System.setProperty(key, "none");
    }
    try {
      io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder builder = io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
          .builder()
          .addPropertiesSupplier(() -> defaults).disableShutdownHook();
      callbacks.register(builder);
      try (io.opentelemetry.sdk.OpenTelemetrySdk sdk = builder.build().getOpenTelemetrySdk()) {
        assertThat(callbacks.isUsed()).isFalse();
        verify(provider, never()).ensureUsable();
        verify(provider, never()).headers();
      }
    } finally {
      previous.forEach((key, value) -> {
        if (value == null) {
          System.clearProperty(key);
        } else {
          System.setProperty(key, value);
        }
      });
    }
  }
}
