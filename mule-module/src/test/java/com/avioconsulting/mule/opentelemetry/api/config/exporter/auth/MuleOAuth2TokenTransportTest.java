package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.MuleOAuth2TokenClient;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import org.junit.Test;
import org.junit.After;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;
import org.mule.runtime.extension.api.runtime.config.ConfigurationProvider;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * Real injected Mule HTTP service, HTTPS fixture and public token client
 * contract.
 */
public class MuleOAuth2TokenTransportTest extends com.avioconsulting.mule.opentelemetry.AbstractMuleArtifactTraceTest {
  @Override
  public void clearSpansQueue() {
    /* Token service traffic does not generate processor spans. */ }

  private final Map<String, String> previous = new HashMap<>();
  private SSLContext previousTls;

  @Override
  protected String getConfigFile() {
    return "exporter-authentication.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    set("otel.sdk.disabled", "true");
    set("javax.net.ssl.trustStore",
        new File(getClass().getResource("/authentication/localhost.p12").toURI()).getPath());
    set("javax.net.ssl.trustStorePassword", "dummy-password");
    set("javax.net.ssl.trustStoreType", "PKCS12");
    previousTls = SSLContext.getDefault();
    KeyStore trust = KeyStore.getInstance("PKCS12");
    try (InputStream input = getClass().getResourceAsStream("/authentication/localhost.p12")) {
      trust.load(input, "dummy-password".toCharArray());
    }
    javax.net.ssl.TrustManagerFactory managers = javax.net.ssl.TrustManagerFactory.getInstance(
        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
    managers.init(trust);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, managers.getTrustManagers(), null);
    SSLContext.setDefault(context);
    super.doSetUpBeforeMuleContextCreation();
  }

  private void set(String key, String value) {
    previous.put(key, System.getProperty(key));
    System.setProperty(key, value);
  }

  @After
  public void restoreProperties() {
    if (previousTls != null) {
      SSLContext.setDefault(previousTls);
    }
    previous.forEach((key, value) -> {
      if (value == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, value);
      }
    });
  }

  @Test
  public void postsBothCredentialPlacementsOverValidatedTlsAndRefusesRedirects() throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream input = getClass().getResourceAsStream("/authentication/localhost.p12")) {
      keys.load(input, "dummy-password".toCharArray());
    }
    KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, "dummy-password".toCharArray());
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(managers.getKeyManagers(), null, null);
    HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
    List<String> requests = new CopyOnWriteArrayList<>();
    List<String> basic = new CopyOnWriteArrayList<>();
    server.createContext("/token", exchange -> {
      try {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream input = exchange.getRequestBody()) {
          int b;
          while ((b = input.read()) != -1) {
            body.write(b);
          }
        }
        requests.add(new String(body.toByteArray(), StandardCharsets.UTF_8));
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) {
          basic.add(authorization);
        }
        byte[] response = "{\"access_token\":\"dummy-token\",\"token_type\":\"Bearer\",\"expires_in\":60}"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
      } finally {
        exchange.close();
      }
    });
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().set("Location", "/token");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.start();
    ConfigurationProvider configProvider = (ConfigurationProvider) registry.lookupByName("oauth").get();
    OpenTelemetryExtensionConfiguration config = (OpenTelemetryExtensionConfiguration) configProvider.get(null)
        .getValue();
    String endpoint = "https://localhost:" + server.getAddress().getPort();
    try {
      for (OAuth2Auth.CredentialPlacement placement : OAuth2Auth.CredentialPlacement.values()) {
        OAuth2Auth auth = new OAuth2Auth(endpoint + "/token", "id:+", "secret &", null)
            .setCredentialPlacement(placement);
        try (MuleOAuth2TokenClient client = new MuleOAuth2TokenClient(config.getHttpService(), auth)) {
          assertThat(client.acquire().getValue()).isEqualTo("dummy-token");
        }
      }
      assertThat(requests).containsExactly(
          "grant_type=client_credentials&client_id=id%3A%2B&client_secret=secret+%26",
          "grant_type=client_credentials");
      assertThat(basic).containsExactly("Basic "
          + Base64.getEncoder().encodeToString("id%3A%2B:secret+%26".getBytes(StandardCharsets.UTF_8)));
      try (MuleOAuth2TokenClient client = new MuleOAuth2TokenClient(config.getHttpService(),
          new OAuth2Auth(endpoint + "/redirect", "id", "secret", null))) {
        assertThatThrownBy(client::acquire).hasMessageContaining("HTTP").hasCause(null);
      }
      assertThat(requests).hasSize(2);
    } finally {
      server.stop(0);
    }
  }
}
