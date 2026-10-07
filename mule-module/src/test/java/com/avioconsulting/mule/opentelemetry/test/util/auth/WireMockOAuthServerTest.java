package com.avioconsulting.mule.opentelemetry.test.util.auth;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class WireMockOAuthServerTest {
  @Test
  public void servesRotatingTokensAndOutagesOverTrustedHttps() throws Exception {
    try (AuthenticationTlsFixture tls = new AuthenticationTlsFixture();
        WireMockOAuthServer oauth = new WireMockOAuthServer()) {
      assertThat(post(oauth, tls)).contains("dummy-token-A");
      oauth.token("dummy-token-B");
      assertThat(post(oauth, tls)).contains("dummy-token-B");
      oauth.respond(aResponse().withStatus(503));
      assertThatThrownBy(() -> post(oauth, tls)).isInstanceOf(java.io.IOException.class);
      assertThat(oauth.requests()).isEqualTo(3);
      oauth.server().verify(3, postRequestedFor(urlEqualTo(WireMockOAuthServer.PATH))
          .withRequestBody(equalTo("grant_type=client_credentials")));
    }
  }

  private String post(WireMockOAuthServer oauth, AuthenticationTlsFixture tls) throws Exception {
    HttpsURLConnection connection = (HttpsURLConnection) new URL(oauth.tokenUrl()).openConnection();
    connection.setSSLSocketFactory(tls.serverContext().getSocketFactory());
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(3000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    try {
      try (OutputStream output = connection.getOutputStream()) {
        output.write("grant_type=client_credentials".getBytes(StandardCharsets.UTF_8));
      }
      try (InputStream input = connection.getInputStream()) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
          output.write(value);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }
}
