package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.*;
import com.avioconsulting.mule.opentelemetry.test.util.auth.AuthenticationTlsFixture;
import com.avioconsulting.mule.opentelemetry.test.util.auth.LocalOtlpCollector;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused transport parity tests; end-to-end suites additionally use real token
 * acquisition.
 */
public class OtlpAuthenticationTransportTest {
  @Test
  public void rotatesAllHttpSignalsWithoutRebuildingExporters() throws Exception {
    exercise(LocalOtlpCollector.Protocol.HTTP_PROTOBUF);
  }

  @Test
  public void rotatesAllGrpcSignalsOnRealTlsCollector() throws Exception {
    exercise(LocalOtlpCollector.Protocol.GRPC);
  }

  private void exercise(LocalOtlpCollector.Protocol protocol) throws Exception {
    try (AuthenticationTlsFixture tls = new AuthenticationTlsFixture();
        LocalOtlpCollector collector = new LocalOtlpCollector(protocol, tls)) {
      exercise(protocol == LocalOtlpCollector.Protocol.GRPC, collector, tls.certificate(), tls.privateKey());
    }
  }

  private void exercise(boolean grpc, LocalOtlpCollector collector, byte[] cert, byte[] key) {
    AtomicLong clock = new AtomicLong();
    OAuth2TokenClient client = mock(OAuth2TokenClient.class);
    when(client.acquire()).thenReturn(new OAuth2AccessToken("A", 10), new OAuth2AccessToken("B", 10));
    OAuth2TokenProvider provider = new OAuth2TokenProvider(client, 60, 1000, clock::get, () -> 0.5);
    OtlpAuthenticationCustomizer customizer = new OtlpAuthenticationCustomizer(provider);
    String endpoint = collector.endpoint();
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getString("otel.exporter.otlp.endpoint")).thenReturn(endpoint);
    when(properties.getMap("otel.exporter.otlp.headers"))
        .thenReturn(Collections.singletonMap("X-Tenant", "dummy-tenant"));
    SpanExporter spans = customizer.spans(grpc
        ? OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpSpanExporter.builder().setEndpoint(endpoint + "/v1/traces").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build(),
        properties);
    MetricExporter metrics = customizer.metrics(grpc
        ? OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpMetricExporter.builder().setEndpoint(endpoint + "/v1/metrics").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build(),
        properties);
    LogRecordExporter logs = customizer.logs(grpc
        ? OtlpGrpcLogRecordExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint + "/v1/logs").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip").addHeader("X-Tenant", "dummy-tenant").build(),
        properties);
    try {
      assertThat(spans.toString()).doesNotContain("dummy");
      assertThat(metrics.toString()).doesNotContain("dummy");
      assertThat(logs.toString()).doesNotContain("dummy");
      verify(client, never()).acquire();
      Collection<MetricData> data;
      InMemoryMetricReader reader = InMemoryMetricReader.create();
      try (SdkMeterProvider meter = SdkMeterProvider.builder().registerMetricReader(reader).build()) {
        meter.get("test").counterBuilder("dummy-counter").build().add(1);
        data = reader.collectAllMetrics();
      }
      for (int round = 0; round < 2; round++) {
        clock.set(TimeUnit.SECONDS.toNanos(round * 5));
        success(spans.export(Collections.singletonList(TestSpanData.builder().setName("dummy-span")
            .setKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
            .setStatus(io.opentelemetry.sdk.trace.data.StatusData.unset())
            .setStartEpochNanos(1).setEndEpochNanos(2).setHasEnded(true).build())));
        success(metrics.export(data));
        success(logs
            .export(Collections.singletonList(TestLogRecordData.builder().setBody("dummy-log").build())));
      }
      assertThat(collector.failures()).isEmpty();
      assertThat(collector.exports()).hasSize(6);
      assertThat(collector.exports().stream().filter(value -> value.authorization.contains("Bearer A")).count())
          .isEqualTo(3);
      assertThat(collector.exports().stream().filter(value -> value.authorization.contains("Bearer B")).count())
          .isEqualTo(3);
      collector.exports().forEach(value -> {
        assertThat(value.authorization).hasSize(1);
        assertThat(value.tenant).containsExactly("dummy-tenant");
        assertThat(value.compressed).isTrue();
      });
      verify(client, times(2)).acquire();
      clock.set(TimeUnit.SECONDS.toNanos(16));
      when(client.acquire())
          .thenThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.HTTP));
      assertThat(metrics.export(data).isSuccess()).isFalse();
      assertThat(logs.export(Collections.singletonList(TestLogRecordData.builder().setBody("dummy-log").build()))
          .isSuccess()).isFalse();
      assertThat(collector.exports()).hasSize(6);
      clock.set(TimeUnit.SECONDS.toNanos(17));
      doReturn(new OAuth2AccessToken("C", 10)).when(client).acquire();
      success(metrics.export(data));
      assertThat(collector.exports()).hasSize(7);
      assertThat(collector.exports().stream().filter(value -> value.authorization.contains("Bearer C")).count())
          .isEqualTo(1);
    } finally {
      spans.shutdown().join(5, TimeUnit.SECONDS);
      metrics.shutdown().join(5, TimeUnit.SECONDS);
      logs.shutdown().join(5, TimeUnit.SECONDS);
      provider.close();
    }
  }

  private static void success(CompletableResultCode result) {
    assertThat(result.join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
  }
}
