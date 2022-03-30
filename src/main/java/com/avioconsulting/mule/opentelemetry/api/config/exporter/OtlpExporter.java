package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.Header;
import com.avioconsulting.mule.opentelemetry.api.config.KeyValuePair;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OtlpExporter extends AbstractExporter {

  @Parameter
  @Optional
  @Summary(value = "The OTLP traces, metrics, and logs endpoint to connect to. Must be a URL with a scheme of either http or https based on the use of TLS."
      +
      "GRPC Protocol may have URL like http://localhost:4317 and HTTP_PROTOBUF be like http://localhost:4317/v1.")
  @Example(value = "http://localhost:4317")
  private String collectorEndpoint;

  @Parameter
  @Optional(defaultValue = "GRPC")
  @DisplayName(value = "Collector Protocol")
  private Protocol protocol;

  @Parameter
  @DisplayName("Headers")
  @Optional
  @NullSafe
  @Summary("Key-value pairs separated by commas to pass as request headers on OTLP trace, metric, and log requests.")
  private List<Header> headers;

  public List<Header> getHeaders() {
    return headers;
  }

  public String getCollectorEndpoint() {
    return collectorEndpoint;
  }

  public Map<String, String> getExporterProperties() {
    Map<String, String> config = super.getExporterProperties();
    config.put(OTEL_TRACES_EXPORTER_KEY, "otlp");
    config.put("otel.exporter.otlp.protocol", protocol.getValue());
    config.put("otel.exporter.otlp.endpoint", getCollectorEndpoint());
    // Set Traces endpoint when using HTTP/Protobuf only
    if (protocol.equals(Protocol.HTTP_PROTOBUF)) {
      config.put("otel.exporter.otlp.traces.endpoint", getSignalEndpoint("traces"));
    }
    config.put("otel.exporter.otlp.headers", KeyValuePair.commaSeparatedList(getHeaders()));
    return Collections.unmodifiableMap(config);
  }

  private String getSignalEndpoint(String signal) {
    String endpoint = getCollectorEndpoint();
    if (!endpoint.endsWith("/"))
      endpoint = endpoint.concat("/");
    return endpoint.concat(signal);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    OtlpExporter that = (OtlpExporter) o;
    return Objects.equals(collectorEndpoint, that.collectorEndpoint) && protocol == that.protocol
        && Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(collectorEndpoint, protocol, headers);
  }

  public static enum Protocol {
    GRPC("grpc"), HTTP_PROTOBUF("http/protobuf");

    private final String value;
    private static Map<String, Protocol> protocols;

    Protocol(String value) {
      this.value = value;
    }

    static {
      protocols = Arrays.stream(Protocol.values())
          .collect(Collectors.toMap(Protocol::getValue, Function.identity()));
    }

    public String getValue() {
      return value;
    }

    Protocol fromValue(String value) {
      return protocols.get(value);
    }
  }
}
