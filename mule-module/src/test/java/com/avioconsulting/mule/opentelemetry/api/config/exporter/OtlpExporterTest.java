package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.Header;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OtlpExporterTest {

  @Test
  public void explicitSignalEndpointsArePreservedForBothProtocols() {
    for (Protocol protocol : Protocol.values()) {
      OtlpExporter exporter = exporter("https://collector.example.com", protocol)
          .setTracesEndpoint("https://traces.example.com/custom/trace?api-version=1&tenant=example")
          .setMetricsEndpoint("https://metrics.example.com/custom/metric/")
          .setLogsEndpoint("https://logs.example.com/custom/log?api-version=2");
      assertThat(exporter.getExporterProperties())
          .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, exporter.getTracesEndpoint())
          .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, exporter.getMetricsEndpoint())
          .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, exporter.getLogsEndpoint());
    }
  }

  @Test
  public void partialHttpOverrideRetainsCollectorFallback() {
    OtlpExporter exporter = exporter("https://collector.example.com", Protocol.HTTP_PROTOBUF)
        .setLogsEndpoint("https://logs.example.com/custom");
    assertThat(exporter.getExporterProperties())
        .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, "https://collector.example.com/v1/traces")
        .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, "https://collector.example.com/v1/metrics")
        .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, "https://logs.example.com/custom");
  }

  @Test
  public void partialGrpcOverrideUsesSharedEndpointForOtherSignals() {
    OtlpExporter exporter = exporter("https://collector.example.com:4317", Protocol.GRPC)
        .setLogsEndpoint("https://logs.example.com:4317");
    assertThat(exporter.getExporterProperties())
        .containsEntry(OTEL_EXPORTER_OTLP_ENDPOINT, "https://collector.example.com:4317")
        .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, "https://logs.example.com:4317")
        .doesNotContainKeys(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, OTEL_EXPORTER_OTLP_METRICS_ENDPOINT);
  }

  @Test
  public void configPropertiesSignalEndpointsAreNotOverwrittenByDerivedEndpoints() {
    for (Protocol protocol : Protocol.values()) {
      OtlpExporter exporter = exporter("https://collector.example.com", protocol);
      Map<String, String> endpoints = signalEndpoints("https://override.example.com/");
      exporter.setConfigProperties(endpoints);
      assertThat(exporter.getExporterProperties()).containsAllEntriesOf(endpoints);
    }
  }

  @Test
  public void explicitAttributesOverrideConfigProperties() {
    OtlpExporter exporter = exporter("https://collector.example.com", Protocol.HTTP_PROTOBUF)
        .setTracesEndpoint("https://traces.example.com/custom")
        .setMetricsEndpoint("https://metrics.example.com/custom")
        .setLogsEndpoint("https://logs.example.com/custom");
    exporter.setConfigProperties(signalEndpoints("https://override.example.com/"));
    assertThat(exporter.getExporterProperties())
        .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, exporter.getTracesEndpoint())
        .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, exporter.getMetricsEndpoint())
        .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, exporter.getLogsEndpoint());
  }

  @Test
  public void collectorCanBeOmittedWithExplicitSignalEndpoints() {
    for (Protocol protocol : Protocol.values()) {
      OtlpExporter exporter = exporter(null, protocol)
          .setTracesEndpoint("https://traces.example.com/custom")
          .setMetricsEndpoint("https://metrics.example.com/custom")
          .setLogsEndpoint("https://logs.example.com/custom");
      assertThat(exporter.getCollectorEndpoint()).isNull();
      assertThat(exporter.getExporterProperties())
          .doesNotContainKey(OTEL_EXPORTER_OTLP_ENDPOINT)
          .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, exporter.getTracesEndpoint())
          .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, exporter.getMetricsEndpoint())
          .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, exporter.getLogsEndpoint());
    }
  }

  @Test
  public void collectorCanBeOmittedWithSdkConfigurationOrDefaults() {
    OtlpExporter exporter = exporter(null, Protocol.HTTP_PROTOBUF);
    assertThat(exporter.getExporterProperties()).doesNotContainKeys(OTEL_EXPORTER_OTLP_ENDPOINT,
        OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
        OTEL_EXPORTER_OTLP_LOGS_ENDPOINT);
    Map<String, String> properties = signalEndpoints("https://override.example.com/");
    properties.put(OTEL_EXPORTER_OTLP_ENDPOINT, "https://collector.example.com");
    exporter.setConfigProperties(properties);
    assertThat(exporter.getExporterProperties()).containsAllEntriesOf(properties);
  }

  @Test
  public void signalEndpointsParticipateInEqualityAndHashCode() {
    OtlpExporter first = exporter("https://collector.example.com", Protocol.HTTP_PROTOBUF);
    OtlpExporter second = exporter("https://collector.example.com", Protocol.HTTP_PROTOBUF);
    assertThat(first).isEqualTo(second);
    first.setTracesEndpoint("https://traces.example.com/custom");
    assertThat(first).isNotEqualTo(second);
    second.setTracesEndpoint(first.getTracesEndpoint());
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    first.setMetricsEndpoint("https://metrics.example.com/custom");
    assertThat(first).isNotEqualTo(second);
    second.setMetricsEndpoint(first.getMetricsEndpoint());
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    first.setLogsEndpoint("https://logs.example.com/custom");
    assertThat(first).isNotEqualTo(second);
    second.setLogsEndpoint(first.getLogsEndpoint());
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  public void systemPropertiesOverrideSignalAttributesThroughSdkAutoconfiguration() {
    OtlpExporter exporter = exporter(null, Protocol.HTTP_PROTOBUF)
        .setTracesEndpoint("https://traces.example.com/custom")
        .setMetricsEndpoint("https://metrics.example.com/custom")
        .setLogsEndpoint("https://logs.example.com/custom");
    Map<String, String> overrides = signalEndpoints("https://system.example.com/");
    Map<String, String> previous = new HashMap<>();
    overrides.forEach((key, value) -> {
      previous.put(key, System.getProperty(key));
      System.setProperty(key, value);
    });
    Map<String, String> defaults = exporter.getExporterProperties();
    defaults.put(OTEL_TRACES_EXPORTER_KEY, "none");
    defaults.put(OTEL_METRICS_EXPORTER_KEY, "none");
    defaults.put(OTEL_LOGS_EXPORTER_KEY, "none");
    try {
      io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder builder = io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
          .builder().addPropertiesSupplier(() -> defaults).disableShutdownHook();
      builder.addPropertiesCustomizer(properties -> {
        overrides.forEach((key, value) -> assertThat(properties.getString(key)).isEqualTo(value));
        return Collections.emptyMap();
      });
      try (io.opentelemetry.sdk.OpenTelemetrySdk sdk = builder.build().getOpenTelemetrySdk()) {
        assertThat(sdk).isNotNull();
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

  private OtlpExporter exporter(String endpoint, Protocol protocol) {
    return new OtlpExporter(endpoint, protocol, OtlpRequestCompression.NONE, Collections.emptyList());
  }

  private Map<String, String> signalEndpoints(String prefix) {
    Map<String, String> endpoints = new HashMap<>();
    endpoints.put(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, prefix + "trace");
    endpoints.put(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, prefix + "metric");
    endpoints.put(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, prefix + "log");
    return endpoints;
  }

  @Test
  public void verifyConfigProperties() {
    java.util.List<Header> headers = new java.util.ArrayList<>();
    headers.add(new Header("test", "value"));
    headers.add(new Header("test2", "value2"));
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost/v1", Protocol.HTTP_PROTOBUF,
        OtlpRequestCompression.GZIP, headers);
    assertThat(otlpExporter.getExporterProperties())
        .containsEntry(OTEL_TRACES_EXPORTER_KEY, OTLP)
        .containsEntry(OTEL_EXPORTER_OTLP_COMPRESSION, OtlpRequestCompression.GZIP.getValue())
        .containsEntry(OTEL_EXPORTER_OTLP_PROTOCOL, Protocol.HTTP_PROTOBUF.getValue())
        .containsEntry(OTEL_EXPORTER_OTLP_HEADERS, "test=value,test2=value2")
        .containsEntry(OTEL_EXPORTER_OTLP_ENDPOINT, "http://localhost/v1")
        .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, "http://localhost/v1/traces")
        .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, "http://localhost/v1/logs")
        .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, "http://localhost/v1/metrics");
  }

  @Test
  public void verifyHTTPProtobufEndpointIsAmended() {
    java.util.List<Header> headers = new java.util.ArrayList<>();
    headers.add(new Header("test", "value"));
    headers.add(new Header("test2", "value2"));
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost", OtlpExporter.Protocol.HTTP_PROTOBUF,
        OtlpExporter.OtlpRequestCompression.GZIP, headers);
    assertThat(otlpExporter.getExporterProperties())
        .containsEntry(OTEL_TRACES_EXPORTER_KEY, OTLP)
        .containsEntry(OTEL_EXPORTER_OTLP_COMPRESSION, OtlpExporter.OtlpRequestCompression.GZIP.getValue())
        .containsEntry(OTEL_EXPORTER_OTLP_PROTOCOL, Protocol.HTTP_PROTOBUF.getValue())
        .containsEntry(OTEL_EXPORTER_OTLP_HEADERS, "test=value,test2=value2")
        .containsEntry(OTEL_EXPORTER_OTLP_ENDPOINT, "http://localhost/v1")
        .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, "http://localhost/v1/traces")
        .containsEntry(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, "http://localhost/v1/logs")
        .containsEntry(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, "http://localhost/v1/metrics");
  }

  @Test
  public void verifyNoCompressionSet() {
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost", Protocol.HTTP_PROTOBUF,
        OtlpRequestCompression.NONE, Collections.emptyList());
    assertThat(otlpExporter.getExporterProperties())
        .doesNotContainKey(OTEL_EXPORTER_OTLP_COMPRESSION);
  }

  @Test
  public void verifyTransformedCertificatePaths() {
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost", Protocol.HTTP_PROTOBUF,
        OtlpRequestCompression.NONE, Collections.emptyList(), "./certs/server-all-certs.pem",
        "./certs/client-key-pkcs8.pem", "./certs/client-cert.pem");
    assertThat(otlpExporter.getExporterProperties())
        .containsEntry(OTEL_EXPORTER_OTLP_CERTIFICATE,
            Objects.requireNonNull(
                this.getClass().getClassLoader().getResource("./certs/server-all-certs.pem"))
                .getPath())
        .containsEntry(OTEL_EXPORTER_OTLP_CLIENT_KEY,
            Objects.requireNonNull(
                this.getClass().getClassLoader().getResource("./certs/client-key-pkcs8.pem"))
                .getPath())
        .containsEntry(OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE, Objects
            .requireNonNull(this.getClass().getClassLoader().getResource("./certs/client-cert.pem"))
            .getPath());
  }

  @Test
  public void verifyNotTransformedCertificatePaths() {
    String serverAllCert = Paths.get("src/test/resources/certs/server-all-certs.pem").toAbsolutePath().toString();
    String clientKey = Paths.get("src/test/resources/certs/client-key-pkcs8.pem").toAbsolutePath().toString();
    String clientCert = Paths.get("src/test/resources/certs/client-cert.pem").toAbsolutePath().toString();
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost", Protocol.HTTP_PROTOBUF,
        OtlpRequestCompression.NONE, Collections.emptyList(), serverAllCert, clientKey,
        clientCert);
    assertThat(otlpExporter.getExporterProperties())
        .containsEntry(OTEL_EXPORTER_OTLP_CERTIFICATE,
            serverAllCert)
        .containsEntry(OTEL_EXPORTER_OTLP_CLIENT_KEY, clientKey)
        .containsEntry(OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE, clientCert);
  }

}
