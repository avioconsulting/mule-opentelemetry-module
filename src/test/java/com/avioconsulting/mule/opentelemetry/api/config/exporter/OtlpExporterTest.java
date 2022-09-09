package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.Header;
import org.junit.Test;

import java.util.Collections;

import static com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OtlpExporterTest {

  @Test
  public void verifyConfigProperties() {
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
        .containsEntry(OTEL_EXPORTER_OTLP_ENDPOINT, "http://localhost")
        .containsEntry(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, "http://localhost/traces");
  }

  @Test
  public void verifyNoCompressionSet() {
    OtlpExporter otlpExporter = new OtlpExporter("http://localhost", OtlpExporter.Protocol.HTTP_PROTOBUF,
        OtlpExporter.OtlpRequestCompression.NONE, Collections.emptyList());
    assertThat(otlpExporter.getExporterProperties())
        .doesNotContainKey(OTEL_EXPORTER_OTLP_COMPRESSION);
  }

}