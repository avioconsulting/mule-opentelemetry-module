package com.avioconsulting.mule.opentelemetry.test.util.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** HTTPS OAuth endpoint only; the production Mule token client remains real. */
public final class WireMockOAuthServer implements AutoCloseable {
  public static final String PATH = "/oauth2/token";
  private final AtomicReference<ResponseDefinition> response = new AtomicReference<>();
  private final WireMockServer server;
  private final String tokenUrl;
  private volatile CountDownLatch entered;
  private volatile CountDownLatch release;

  public WireMockOAuthServer() throws Exception {
    this(true);
  }

  public WireMockOAuthServer(boolean trustedIdentity) throws Exception {
    com.github.tomakehurst.wiremock.core.WireMockConfiguration options = wireMockConfig()
        .httpDisabled(true).dynamicHttpsPort().bindAddress("localhost")
        .notifier(new ConsoleNotifier(false)).stubRequestLoggingDisabled(true)
        .extensions(new ResponseDefinitionTransformer() {
          @Override
          public String getName() {
            return "local-oauth-response";
          }

          @Override
          public boolean applyGlobally() {
            return false;
          }

          @Override
          public ResponseDefinition transform(Request request, ResponseDefinition definition,
              FileSource files, Parameters parameters) {
            CountDownLatch waiting = release;
            CountDownLatch seen = entered;
            if (seen != null) {
              seen.countDown();
            }
            if (waiting != null) {
              try {
                if (!waiting.await(5, TimeUnit.SECONDS)) {
                  return aResponse().withStatus(503).build();
                }
              } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return aResponse().withStatus(503).build();
              }
            }
            return response.get();
          }
        });
    if (trustedIdentity) {
      options.keystorePath(AuthenticationTlsFixture.keyStorePath()).keystoreType("PKCS12")
          .keystorePassword(AuthenticationTlsFixture.PASSWORD)
          .keyManagerPassword(AuthenticationTlsFixture.PASSWORD);
    }
    server = new WireMockServer(options);
    token("dummy-token-A");
    try {
      server.start();
      tokenUrl = "https://localhost:" + server.httpsPort() + PATH;
      server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withTransformers("local-oauth-response")));
    } catch (RuntimeException failure) {
      server.stop();
      throw failure;
    }
  }

  public String tokenUrl() {
    return tokenUrl;
  }

  public WireMockServer server() {
    return server;
  }

  public int requests() {
    return server.findAll(postRequestedFor(urlEqualTo(PATH))).size();
  }

  public void token(String token) {
    respond(aResponse().withHeader("Content-Type", "application/json")
        .withBody("{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":120}"));
  }

  public void respond(ResponseDefinitionBuilder response) {
    this.response.set(response.build());
  }

  public void blockNextRequest(CountDownLatch entered, CountDownLatch release) {
    this.entered = entered;
    this.release = release;
  }

  public void unblock() {
    CountDownLatch waiting = release;
    if (waiting != null) {
      waiting.countDown();
    }
    release = null;
    entered = null;
  }

  @Override
  public void close() {
    unblock();
    server.stop();
  }
}
