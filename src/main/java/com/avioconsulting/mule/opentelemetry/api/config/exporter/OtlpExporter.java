package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import com.avioconsulting.mule.opentelemetry.api.config.Header;
import com.avioconsulting.mule.opentelemetry.api.config.KeyValuePair;
import org.mule.runtime.api.meta.model.display.PathModel;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Path;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OtlpExporter extends AbstractExporter {

  public static final String OTLP = "otlp";
  public static final String OTEL_EXPORTER_OTLP_PROTOCOL = "otel.exporter.otlp.protocol";
  public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "otel.exporter.otlp.endpoint";
  public static final String OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "otel.exporter.otlp.traces.endpoint";
  public static final String OTEL_EXPORTER_OTLP_METRICS_ENDPOINT = "otel.exporter.otlp.metrics.endpoint";
  public static final String OTEL_EXPORTER_OTLP_LOGS_ENDPOINT = "otel.exporter.otlp.logs.endpoint";
  public static final String OTEL_EXPORTER_OTLP_COMPRESSION = "otel.exporter.otlp.compression";
  public static final String OTEL_EXPORTER_OTLP_HEADERS = "otel.exporter.otlp.headers";
  public static final String OTEL_EXPORTER_OTLP_CERTIFICATE = "otel.exporter.otlp.certificate";
  public static final String OTEL_EXPORTER_OTLP_CLIENT_KEY = "otel.exporter.otlp.client.key";
  public static final String OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE = "otel.exporter.otlp.client.certificate";
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
  @Optional(defaultValue = "NONE")
  @DisplayName(value = "Request Compression")
  @Summary("The compression type to use on OTLP trace, metric, and log requests.")
  private OtlpRequestCompression requestCompression;

  @Parameter
  @Optional
  @Path(type = PathModel.Type.FILE, acceptedFileExtensions = { "pem" }, location = PathModel.Location.EMBEDDED)
  @DisplayName(value = "Endpoint Certificate Path")
  @Summary("The path to the file containing trusted certificates to use when verifying an OTLP trace, metric, or log server's TLS credentials. The file should contain one or more X.509 certificates in PEM format. By default the host platform's trusted root certificates are used.")
  private String endpointCertPath;

  @Parameter
  @Optional
  @Path(type = PathModel.Type.FILE, acceptedFileExtensions = { "pem" }, location = PathModel.Location.EMBEDDED)
  @DisplayName(value = "Client Certificate Path")
  @Summary("The path to the file containing trusted certificates to use when verifying an OTLP trace, metric, or log client's TLS credentials. The file should contain one or more X.509 certificates in PEM format. By default no chain file is used.")
  private String clientCertPath;

  @Parameter
  @Optional
  @Path(type = PathModel.Type.FILE, acceptedFileExtensions = { "pem" }, location = PathModel.Location.EMBEDDED)
  @DisplayName(value = "Client Certificate Key Path")
  @Summary("The path to the file containing private client key to use when verifying an OTLP trace, metric, or log client's TLS credentials. The file should contain one private key PKCS8 PEM format. By default no client key is used.")
  private String clientCertKeyPath;

  @Parameter
  @DisplayName("Headers")
  @Optional
  @NullSafe
  @Summary("Key-value pairs separated by commas to pass as request headers on OTLP trace, metric, and log requests.")
  private List<Header> headers;

  public List<Header> getHeaders() {
    return headers;
  }

  public OtlpRequestCompression getRequestCompression() {
    return requestCompression;
  }

  public String getCollectorEndpoint() {
    return collectorEndpoint;
  }

  public String getEndpointCertPath() {
    return endpointCertPath;
  }

  public String getClientCertPath() {
    return clientCertPath;
  }

  public String getClientCertKeyPath() {
    return clientCertKeyPath;
  }

  /**
   * Default constructor used by Mule SDK to instantiate this class.
   */
  public OtlpExporter() {

  }

  /**
   * Constructor used for testing purpose.
   *
   * @param collectorEndpoint
   *            where span are delivered.
   * @param protocol
   *            {@link Protocol} used by the collector
   * @param requestCompression
   *            {@link OtlpRequestCompression} to use for request compression
   * @param headers
   *            {@link List} of {@link Header} elements to send to collector
   */
  OtlpExporter(String collectorEndpoint, Protocol protocol, OtlpRequestCompression requestCompression,
      List<Header> headers) {
    this(collectorEndpoint, protocol, requestCompression, headers, null, null, null);
  }

  /**
   * Constructor used for testing purpose.
   *
   * @param collectorEndpoint
   *            where span are delivered.
   * @param protocol
   *            {@link Protocol} used by the collector
   * @param requestCompression
   *            {@link OtlpRequestCompression} to use for request compression
   * @param headers
   *            {@link List} of {@link Header} elements to send to collector
   * @param endpointCertPath
   *            {@link String} OTLP Endpoint Certificate Path
   * @param clientCertKeyPath
   *            {@link String} OTLP Client Certificate Key Path
   * @param clientCertPath
   *            {@link String} OTLP Client Certificate Path
   */
  OtlpExporter(String collectorEndpoint, Protocol protocol, OtlpRequestCompression requestCompression,
      List<Header> headers, String endpointCertPath, String clientCertKeyPath, String clientCertPath) {
    this.collectorEndpoint = collectorEndpoint;
    this.protocol = protocol;
    this.requestCompression = requestCompression;
    this.headers = headers;
    this.endpointCertPath = endpointCertPath;
    this.clientCertPath = clientCertPath;
    this.clientCertKeyPath = clientCertKeyPath;
  }

  public Map<String, String> getExporterProperties() {
    Map<String, String> config = super.getExporterProperties();
    config.put(OTEL_TRACES_EXPORTER_KEY, OTLP);
    config.put(OTEL_METRICS_EXPORTER_KEY, OTLP);
    config.put(OTEL_LOGS_EXPORTER_KEY, OTLP);
    config.put(OTEL_EXPORTER_OTLP_PROTOCOL, protocol.getValue());
    config.put(OTEL_EXPORTER_OTLP_ENDPOINT, getCollectorEndpoint());
    // Set Traces endpoint when using HTTP/Protobuf only
    if (protocol.equals(Protocol.HTTP_PROTOBUF)) {
      config.put(OTEL_EXPORTER_OTLP_TRACES_ENDPOINT, getSignalEndpoint("traces"));
      config.put(OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, getSignalEndpoint("metrics"));
      config.put(OTEL_EXPORTER_OTLP_LOGS_ENDPOINT, getSignalEndpoint("logs"));
    }
    if (!OtlpRequestCompression.NONE.equals(requestCompression)) {
      config.put(OTEL_EXPORTER_OTLP_COMPRESSION, requestCompression.getValue());
    }
    config.put(OTEL_EXPORTER_OTLP_HEADERS, KeyValuePair.commaSeparatedList(getHeaders()));
    if (getEndpointCertPath() != null) {
      config.put(OTEL_EXPORTER_OTLP_CERTIFICATE,
          transformToAbsolutePath(OTEL_EXPORTER_OTLP_CERTIFICATE, getEndpointCertPath()));
    }
    if (getClientCertKeyPath() != null) {
      config.put(OTEL_EXPORTER_OTLP_CLIENT_KEY,
          transformToAbsolutePath(OTEL_EXPORTER_OTLP_CLIENT_KEY, getClientCertKeyPath()));
    }
    if (getClientCertPath() != null) {
      config.put(OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE,
          transformToAbsolutePath(OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE, getClientCertPath()));
    }
    return config;
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

  /**
   * The compression type to use on OTLP trace, metric, and log requests.
   */
  public enum OtlpRequestCompression {
    NONE("none"), GZIP("gzip");

    private final String value;
    private static Map<String, OtlpRequestCompression> compressions;

    OtlpRequestCompression(String value) {
      this.value = value;
    }

    static {
      compressions = Arrays.stream(OtlpRequestCompression.values())
          .collect(Collectors.toMap(OtlpRequestCompression::getValue, Function.identity()));
    }

    public String getValue() {
      return value;
    }

    OtlpRequestCompression fromValue(String value) {
      return compressions.get(value);
    }
  }
}
