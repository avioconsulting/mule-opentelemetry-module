package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.*;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Actual TLS HTTP/protobuf and HTTP/2 gRPC requests for every authenticated
 * signal.
 */
public class OtlpAuthenticationTransportTest {
  private final Queue<String> received = new ConcurrentLinkedQueue<>();

  @Test
  public void rotatesAllHttpSignalsWithoutRebuildingExporters() throws Exception {
    KeyStore keys = keys();
    SSLContext tls = SSLContext.getInstance("TLS");
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(keys);
    tls.init(keyManagers(keys).getKeyManagers(), trust.getTrustManagers(), null);
    HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls) {
      @Override
      public void configure(com.sun.net.httpserver.HttpsParameters parameters) {
        SSLParameters settings = tls.getDefaultSSLParameters();
        settings.setNeedClientAuth(true);
        parameters.setSSLParameters(settings);
      }
    });
    server.createContext("/", exchange -> {
      try {
        assertThat(exchange.getRequestHeaders().get("X-Tenant")).containsExactly("dummy-tenant");
        assertThat(exchange.getRequestHeaders().get("Authorization")).hasSize(1);
        received.add(exchange.getRequestURI().getPath() + ":"
            + exchange.getRequestHeaders().getFirst("Authorization"));
        try (InputStream input = exchange.getRequestBody()) {
          while (input.read() != -1) {
            /* consume */ }
        }
        exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
        exchange.sendResponseHeaders(200, -1);
      } finally {
        exchange.close();
      }
    });
    server.start();
    try {
      exercise(false, server.getAddress().getPort(), certificate(keys), privateKey(keys));
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void rotatesAllGrpcSignalsOnRealTlsCollector() throws Exception {
    KeyStore keys = keys();
    NettyServerBuilder builder = NettyServerBuilder.forAddress(new InetSocketAddress("localhost", 0))
        .sslContext(GrpcSslContexts.configure(SslContextBuilder.forServer(keyManagers(keys)))
            .trustManager(new ByteArrayInputStream(certificate(keys)))
            .clientAuth(io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth.REQUIRE).build());
    for (String signal : Arrays.asList("Trace", "Metrics", "Logs")) {
      String service = "opentelemetry.proto.collector." + signal.toLowerCase(Locale.ROOT)
          + ".v1." + signal + "Service";
      MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
          .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName(service + "/Export")
          .setRequestMarshaller(bytes()).setResponseMarshaller(bytes()).build();
      builder.addService(ServerServiceDefinition.builder(service).addMethod(method, (call, headers) -> {
        Metadata.Key<String> tenant = Metadata.Key.of("x-tenant", Metadata.ASCII_STRING_MARSHALLER);
        Metadata.Key<String> authorization = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headers.getAll(tenant)).containsExactly("dummy-tenant");
        assertThat(headers.getAll(authorization)).hasSize(1);
        received.add(signal + ":" + headers.get(authorization));
        call.request(1);
        return new ServerCall.Listener<byte[]>() {
          @Override
          public void onHalfClose() {
            call.sendHeaders(new Metadata());
            call.sendMessage(new byte[0]);
            call.close(Status.OK, new Metadata());
          }
        };
      }).build());
    }
    Server server = builder.build().start();
    try {
      exercise(true, server.getPort(), certificate(keys), privateKey(keys));
    } finally {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private void exercise(boolean grpc, int port, byte[] cert, byte[] key) {
    AtomicLong clock = new AtomicLong();
    OAuth2TokenClient client = mock(OAuth2TokenClient.class);
    when(client.acquire()).thenReturn(new OAuth2AccessToken("A", 10), new OAuth2AccessToken("B", 10));
    OAuth2TokenProvider provider = new OAuth2TokenProvider(client, 60, 1000, clock::get, () -> 0.5);
    OtlpAuthenticationCustomizer customizer = new OtlpAuthenticationCustomizer(provider);
    String endpoint = "https://localhost:" + port;
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getString("otel.exporter.otlp.endpoint")).thenReturn(endpoint);
    when(properties.getMap("otel.exporter.otlp.headers"))
        .thenReturn(Collections.singletonMap("X-Tenant", "dummy-tenant"));
    SpanExporter spans = customizer.spans(grpc
        ? OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpSpanExporter.builder().setEndpoint(endpoint + "/v1/traces").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build(),
        properties);
    MetricExporter metrics = customizer.metrics(grpc
        ? OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpMetricExporter.builder().setEndpoint(endpoint + "/v1/metrics").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build(),
        properties);
    LogRecordExporter logs = customizer.logs(grpc
        ? OtlpGrpcLogRecordExporter.builder().setEndpoint(endpoint).setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build()
        : OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint + "/v1/logs").setTrustedCertificates(cert)
            .setClientTls(key, cert).setCompression("gzip")
            .addHeader("X-Tenant", "dummy-tenant").build(),
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
      assertThat(received).hasSize(6);
      assertThat(received.stream().filter(value -> value.endsWith("Bearer A")).count()).isEqualTo(3);
      assertThat(received.stream().filter(value -> value.endsWith("Bearer B")).count()).isEqualTo(3);
      verify(client, times(2)).acquire();
      clock.set(TimeUnit.SECONDS.toNanos(16));
      when(client.acquire())
          .thenThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.HTTP));
      assertThat(metrics.export(data).isSuccess()).isFalse();
      assertThat(logs.export(Collections.singletonList(TestLogRecordData.builder().setBody("dummy-log").build()))
          .isSuccess()).isFalse();
      assertThat(received).hasSize(6);
      clock.set(TimeUnit.SECONDS.toNanos(17));
      doReturn(new OAuth2AccessToken("C", 10)).when(client).acquire();
      success(metrics.export(data));
      assertThat(received).hasSize(7);
      assertThat(received.stream().filter(value -> value.endsWith("Bearer C")).count()).isEqualTo(1);
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

  private static KeyStore keys() throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream input = OtlpAuthenticationTransportTest.class
        .getResourceAsStream("/authentication/localhost.p12")) {
      keys.load(input, "dummy-password".toCharArray());
    }
    return keys;
  }

  private static KeyManagerFactory keyManagers(KeyStore keys) throws Exception {
    KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, "dummy-password".toCharArray());
    return managers;
  }

  private static byte[] certificate(KeyStore keys) throws Exception {
    String pem = "-----BEGIN CERTIFICATE-----\n"
        + Base64.getMimeEncoder(64, new byte[] { '\n' })
            .encodeToString(keys.getCertificate("localhost").getEncoded())
        + "\n-----END CERTIFICATE-----\n";
    return pem.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] privateKey(KeyStore keys) throws Exception {
    String pem = "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] { '\n' })
            .encodeToString(keys.getKey("localhost", "dummy-password".toCharArray()).getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
    return pem.getBytes(StandardCharsets.US_ASCII);
  }

  private static MethodDescriptor.Marshaller<byte[]> bytes() {
    return new MethodDescriptor.Marshaller<byte[]>() {
      @Override
      public InputStream stream(byte[] value) {
        return new ByteArrayInputStream(value);
      }

      @Override
      public byte[] parse(InputStream input) {
        try {
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          byte[] buffer = new byte[4096];
          int count;
          while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
          }
          return output.toByteArray();
        } catch (IOException e) {
          throw new IllegalStateException("Test collector request failed");
        }
      }
    };
  }
}
