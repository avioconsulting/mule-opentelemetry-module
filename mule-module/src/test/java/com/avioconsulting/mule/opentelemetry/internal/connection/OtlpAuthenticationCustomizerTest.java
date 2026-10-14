package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.BearerTokenAuth;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.ApiKeyAuth;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.ExporterAuthentication;
import com.avioconsulting.mule.opentelemetry.internal.auth.StaticAuthenticationProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
  public void validatesEffectiveSignalEndpointsInsteadOfSharedEndpoint() {
    ConfigProperties properties = properties();
    when(properties.getString("otel.exporter.otlp.endpoint")).thenReturn("http://localhost:4318");
    when(properties.getString("otel.exporter.otlp.metrics.endpoint"))
        .thenReturn("https://metrics.example.com/custom/");
    OtlpHttpMetricExporter original = OtlpHttpMetricExporter.builder()
        .setEndpoint("https://metrics.example.com/custom/").build();
    MetricExporter replacement = customizer.metrics(original, properties);
    try {
      assertThat(customizer.isUsed()).isTrue();
    } finally {
      replacement.shutdown();
    }
  }

  @Test
  public void rejectsInsecureSignalOverridesEvenWithSecureSharedEndpoint() {
    ConfigProperties properties = properties();
    when(properties.getString("otel.exporter.otlp.traces.endpoint"))
        .thenReturn("http://traces.example.com/custom");
    when(properties.getString("otel.exporter.otlp.metrics.endpoint"))
        .thenReturn("http://metrics.example.com/custom");
    when(properties.getString("otel.exporter.otlp.logs.endpoint"))
        .thenReturn("http://logs.example.com/custom");
    io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter spans = io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
        .builder().build();
    OtlpHttpMetricExporter metrics = OtlpHttpMetricExporter.builder().build();
    io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter logs = io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
        .builder().build();
    try {
      assertThatThrownBy(() -> customizer.spans(spans, properties)).hasMessageContaining("secure");
      assertThatThrownBy(() -> customizer.metrics(metrics, properties)).hasMessageContaining("secure");
      assertThatThrownBy(() -> customizer.logs(logs, properties)).hasMessageContaining("secure");
    } finally {
      spans.shutdown();
      metrics.shutdown();
      logs.shutdown();
    }
  }

  @Test
  public void leavesUnrelatedExportersUnchanged() {
    MetricExporter unrelated = mock(MetricExporter.class);
    assertThat(customizer.metrics(unrelated, properties())).isSameAs(unrelated);
    assertThat(customizer.isUsed()).isFalse();
  }

  @Test
  public void emptySignalHeadersRejectConflictingSharedHeadersThroughSdkAutoconfiguration() {
    for (String protocol : new String[] { "grpc", "http/protobuf" }) {
      for (String signal : new String[] { "traces", "metrics", "logs" }) {
        for (ExporterAuthentication authentication : new ExporterAuthentication[] {
            new BearerTokenAuth("dummy"), new ApiKeyAuth("X-API-Key", "dummy") }) {
          OtlpAuthenticationCustomizer callbacks = new OtlpAuthenticationCustomizer(
              new StaticAuthenticationProvider(authentication));
          AutoConfiguredOpenTelemetrySdkBuilder builder = authenticationBuilder(callbacks, protocol, signal,
              authentication.getHeaderName().toLowerCase(java.util.Locale.ROOT) + "=shared-secret", "");
          assertThatThrownBy(() -> {
            try (OpenTelemetrySdk sdk = builder.build().getOpenTelemetrySdk()) {
              // Close the SDK if the conflicting configuration is unexpectedly accepted.
            }
          }).isInstanceOf(ConfigurationException.class)
              .hasMessageNotContaining("shared-secret")
              .rootCause().isInstanceOf(IllegalArgumentException.class)
              .hasMessage("Exporter authentication conflicts with configured OTLP headers")
              .hasMessageNotContaining("shared-secret");
          assertThat(callbacks.isUsed()).isFalse();
        }
      }
    }
  }

  @Test
  public void nonemptySignalHeadersOverrideConflictingSharedHeadersThroughSdkAutoconfiguration() {
    for (String protocol : new String[] { "grpc", "http/protobuf" }) {
      for (String signal : new String[] { "traces", "metrics", "logs" }) {
        OtlpAuthenticationCustomizer callbacks = new OtlpAuthenticationCustomizer(
            new StaticAuthenticationProvider(new BearerTokenAuth("dummy")));
        AutoConfiguredOpenTelemetrySdkBuilder builder = authenticationBuilder(callbacks, protocol, signal,
            "Authorization=shared-secret", "X-Tenant=dummy-tenant");
        try (OpenTelemetrySdk sdk = builder.build().getOpenTelemetrySdk()) {
          assertThat(callbacks.isUsed()).isTrue();
        }
      }
    }
  }

  private AutoConfiguredOpenTelemetrySdkBuilder authenticationBuilder(OtlpAuthenticationCustomizer callbacks,
      String protocol, String signal, String sharedHeaders, String signalHeaders) {
    Map<String, String> configuration = new HashMap<>();
    for (String configuredSignal : new String[] { "traces", "metrics", "logs" }) {
      configuration.put("otel." + configuredSignal + ".exporter",
          configuredSignal.equals(signal) ? "otlp" : "none");
    }
    String prefix = "otel.exporter.otlp." + signal;
    configuration.put(prefix + ".protocol", protocol);
    configuration.put(prefix + ".endpoint", "https://localhost:12345/");
    configuration.put("otel.exporter.otlp.headers", sharedHeaders);
    configuration.put(prefix + ".headers", signalHeaders);
    // Override Surefire's disabled metrics/log exporters without mutating system
    // properties.
    AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder()
        .addPropertiesCustomizer(properties -> configuration).disableShutdownHook();
    callbacks.register(builder);
    return builder;
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
