package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.Header;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OtlpExporterTest {

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