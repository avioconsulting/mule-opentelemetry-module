package com.avioconsulting.mule.opentelemetry.test.util.auth;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Real local OTLP transports; callbacks capture evidence rather than throwing
 * assertions.
 */
public final class LocalOtlpCollector implements AutoCloseable {
  public enum Protocol {
    HTTP_PROTOBUF, GRPC
  }

  private final Queue<Export> exports = new ConcurrentLinkedQueue<>();
  private final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
  private HttpsServer http;
  private Server grpc;

  public LocalOtlpCollector(Protocol protocol, AuthenticationTlsFixture tls) throws Exception {
    try {
      if (protocol == Protocol.HTTP_PROTOBUF) {
        startHttp(tls);
      } else {
        startGrpc(tls);
      }
    } catch (Exception failure) {
      close();
      throw failure;
    }
  }

  public String endpoint() {
    return "https://localhost:" + (http != null ? http.getAddress().getPort() : grpc.getPort());
  }

  public List<Export> exports() {
    return new ArrayList<>(exports);
  }

  public List<Throwable> failures() {
    return new ArrayList<>(failures);
  }

  public void reset() {
    exports.clear();
    failures.clear();
  }

  private void startHttp(AuthenticationTlsFixture tls) throws Exception {
    SSLContext context = tls.serverContext();
    http = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    http.setHttpsConfigurator(new HttpsConfigurator(context) {
      @Override
      public void configure(HttpsParameters parameters) {
        SSLParameters settings = context.getDefaultSSLParameters();
        settings.setNeedClientAuth(true);
        parameters.setSSLParameters(settings);
      }
    });
    http.createContext("/", exchange -> {
      try {
        boolean compressed = "gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"));
        try (InputStream input = compressed ? new GZIPInputStream(exchange.getRequestBody())
            : exchange.getRequestBody()) {
          exports.add(new Export(exchange.getRequestURI().getPath().replace("/v1/", ""),
              values(exchange.getRequestHeaders().get("Authorization")),
              values(exchange.getRequestHeaders().get("X-Tenant")), read(input), compressed));
        }
        exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
        exchange.sendResponseHeaders(200, -1);
      } catch (Exception failure) {
        failures.add(failure);
      } finally {
        exchange.close();
      }
    });
    http.start();
  }

  private void startGrpc(AuthenticationTlsFixture tls) throws Exception {
    NettyServerBuilder builder = NettyServerBuilder.forAddress(new InetSocketAddress("localhost", 0))
        .sslContext(GrpcSslContexts.configure(SslContextBuilder.forServer(tls.keyManagers()))
            .trustManager(new ByteArrayInputStream(tls.certificate())).clientAuth(ClientAuth.REQUIRE)
            .build());
    for (String name : Arrays.asList("Trace", "Metrics", "Logs")) {
      String signal = "Trace".equals(name) ? "traces" : name.toLowerCase(Locale.ROOT);
      String service = "opentelemetry.proto.collector." + name.toLowerCase(Locale.ROOT) + ".v1." + name
          + "Service";
      MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
          .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName(service + "/Export")
          .setRequestMarshaller(bytes()).setResponseMarshaller(bytes()).build();
      builder.addService(ServerServiceDefinition.builder(service).addMethod(method, (call, headers) -> {
        List<String> auth = values(
            headers.getAll(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
        List<String> tenant = values(
            headers.getAll(Metadata.Key.of("x-tenant", Metadata.ASCII_STRING_MARSHALLER)));
        boolean gzip = "gzip"
            .equals(headers.get(Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER)));
        call.request(1);
        return new ServerCall.Listener<byte[]>() {
          @Override
          public void onMessage(byte[] message) {
            exports.add(new Export(signal, auth, tenant, message, gzip));
          }

          @Override
          public void onHalfClose() {
            call.sendHeaders(new Metadata());
            call.sendMessage(new byte[0]);
            call.close(Status.OK, new Metadata());
          }
        };
      }).build());
    }
    grpc = builder.build().start();
  }

  private static List<String> values(Iterable<String> values) {
    List<String> result = new ArrayList<>();
    if (values != null) {
      values.forEach(result::add);
    }
    return result;
  }

  private static byte[] read(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int count;
    while ((count = input.read(buffer)) != -1) {
      if (output.size() + count > 1024 * 1024) {
        throw new IOException("Test collector request limit exceeded");
      }
      output.write(buffer, 0, count);
    }
    return output.toByteArray();
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
          return read(input);
        } catch (IOException failure) {
          throw new IllegalStateException("Test collector read failed");
        }
      }
    };
  }

  @Override
  public void close() throws Exception {
    if (http != null) {
      http.stop(0);
      http = null;
    }
    if (grpc != null) {
      Server server = grpc;
      grpc = null;
      if (!server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test collector shutdown exceeded budget");
      }
    }
  }

  public static final class Export {
    public final String signal;
    public final List<String> authorization;
    public final List<String> tenant;
    public final byte[] payload;
    public final boolean compressed;

    private Export(String signal, List<String> authorization, List<String> tenant, byte[] payload,
        boolean compressed) {
      this.signal = signal;
      this.authorization = authorization;
      this.tenant = tenant;
      this.payload = payload;
      this.compressed = compressed;
    }

    public boolean contains(String marker) {
      return new String(payload, StandardCharsets.UTF_8).contains(marker);
    }
  }
}
