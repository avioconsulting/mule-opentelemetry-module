package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.AbstractMuleArtifactTraceTest;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.test.util.auth.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Test;
import org.mule.runtime.extension.api.runtime.config.ConfigurationProvider;
import static org.assertj.core.api.Assertions.*;

/**
 * Actual XML-created connection, autoconfiguration, HTTP token client and OTLP
 * transports.
 */
public abstract class AbstractOAuth2ExporterEndToEndTest extends AbstractMuleArtifactTraceTest {
  protected AuthenticationTlsFixture tls;
  protected WireMockOAuthServer oauth;
  protected WireMockOAuthServer untrustedOauth;
  protected LocalOtlpCollector collector;
  protected OpenTelemetryExtensionConfiguration config;
  protected OAuthSdkProbe probe;
  protected final AtomicLong clock = new AtomicLong();
  private final Map<String, String> previous = new HashMap<>();
  private javax.net.ssl.SSLContext previousTls;
  @org.junit.Rule
  public final org.junit.rules.TestName testName = new org.junit.rules.TestName();

  protected abstract LocalOtlpCollector.Protocol protocol();

  protected String placement() {
    return "CLIENT_SECRET_POST";
  }

  @Override
  protected String getConfigFile() {
    return "oauth2-exporter-e2e.xml";
  }

  @Override
  public void clearSpansQueue() {
    /* This suite uses real collectors, not delegatedLogging. */ }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    try {
      tls = new AuthenticationTlsFixture();
      oauth = new WireMockOAuthServer();
      untrustedOauth = new WireMockOAuthServer(false);
      collector = new LocalOtlpCollector(protocol(), tls);
      set("javax.net.ssl.trustStore", AuthenticationTlsFixture.keyStorePath());
      set("javax.net.ssl.trustStorePassword", AuthenticationTlsFixture.PASSWORD);
      set("javax.net.ssl.trustStoreType", "PKCS12");
      previousTls = javax.net.ssl.SSLContext.getDefault();
      javax.net.ssl.SSLContext.setDefault(tls.serverContext());
      set("oauth.e2e.collector", collector.endpoint());
      set("oauth.e2e.protocol", protocol().name());
      set("oauth.e2e.tokenUrl", "tokenTlsDoesNotInheritCollectorTrust".equals(testName.getMethodName())
          ? untrustedOauth.tokenUrl()
          : oauth.tokenUrl());
      set("oauth.e2e.certificate", tls.certificatePath());
      set("oauth.e2e.key", tls.privateKeyPath());
      set("oauth.e2e.placement", placement());
      set("oauth.e2e.timeout", "2000");
      set("otel.sdk.disabled", "false");
      for (String signal : new String[] { "traces", "metrics", "logs" }) {
        set("otel." + signal + ".exporter", "otlp");
      }
      set("otel.metric.export.interval", "3600000");
      set("otel.blrp.schedule.delay", "3600000");
      // Clear inherited endpoint/header overrides; XML must supply the effective
      // configuration.
      for (String signal : new String[] { "", "traces.", "metrics.", "logs." }) {
        for (String key : new String[] { "endpoint", "protocol", "headers", "certificate", "client.key",
            "client.certificate" }) {
          set("otel.exporter.otlp." + signal + key, null);
        }
      }
      super.doSetUpBeforeMuleContextCreation();
      set("otel.bsp.schedule.delay", "3600000");
    } catch (Exception failure) {
      closeFixtures();
      throw failure;
    }
  }

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    ConfigurationProvider provider = (ConfigurationProvider) registry.lookupByName("OAuthEndToEnd").get();
    config = (OpenTelemetryExtensionConfiguration) provider.get(null).getValue();
    assertThat(oauth.requests()).as("SDK construction must not acquire credentials").isZero();
    probe = new OAuthSdkProbe(config.getOpenTelemetryConnection(), clock);
  }

  protected void set(String key, String value) {
    if (!previous.containsKey(key)) {
      previous.put(key, System.getProperty(key));
    }
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  @After
  public void closeFixtures() throws Exception {
    try {
      if (config != null) {
        config.stop();
      }
    } finally {
      try {
        if (oauth != null) {
          oauth.close();
        }
        if (untrustedOauth != null) {
          untrustedOauth.close();
        }
      } finally {
        try {
          if (collector != null) {
            collector.close();
          }
        } finally {
          try {
            if (tls != null) {
              tls.close();
            }
          } finally {
            if (previousTls != null) {
              javax.net.ssl.SSLContext.setDefault(previousTls);
            }
            previous.forEach((key, value) -> {
              if (value == null) {
                System.clearProperty(key);
              } else {
                System.setProperty(key, value);
              }
            });
          }
        }
      }
    }
  }

  @Test
  public void xmlConfiguredSdkAcquiresOneTokenForAllSignals() throws Exception {
    probe.emit("initial");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(1);
    verifyCredentials(1);
    assertThat(collector.failures()).isEmpty();
    assertThat(collector.exports()).extracting(value -> value.signal).contains("traces", "metrics", "logs");
    assertThat(probe.provider.toString()).doesNotContain("dummy-secret", "dummy-token", "dummy-id");
    collector.exports().forEach(value -> {
      assertThat(value.authorization).containsExactly("Bearer dummy-token-A");
      assertThat(value.tenant).containsExactly("dummy-tenant");
      assertThat(value.compressed).isTrue();
      if ("traces".equals(value.signal)) {
        assertThat(value.contains(WireMockOAuthServer.PATH))
            .as("Token HTTP calls must not recursively create processor spans").isFalse();
      }
    });
  }

  @Test
  public void rotatesAllSignalsWithoutReplacingConnectionOrSdk() throws Exception {
    Object connection = config.getOpenTelemetryConnection();
    probe.emit("tokenA");
    probe.flush();
    collector.reset();
    oauth.token("dummy-token-B");
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(70));
    probe.emit("tokenB");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(2);
    verifyCredentials(2);
    assertThat(config.getOpenTelemetryConnection()).isSameAs(connection);
    assertThat(OAuthSdkProbe.field(connection, "sdk")).isSameAs(probe.sdk);
    assertThat(collector.exports()).extracting(value -> value.signal).contains("traces", "metrics", "logs");
    collector.exports().forEach(value -> {
      assertThat(value.authorization).containsExactly("Bearer dummy-token-B");
      assertThat(value.tenant).containsExactly("dummy-tenant");
    });
    for (String signal : new String[] { "traces", "metrics", "logs" }) {
      assertThat(collector.exports().stream()
          .anyMatch(value -> value.signal.equals(signal) && value.contains("tokenB"))).isTrue();
    }
  }

  @Test
  public void outageFallsBackBeforeExpirySuppressesAfterExpiryAndRecovers() throws Exception {
    probe.emit("initial");
    probe.flush();
    collector.reset();
    oauth.respond(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(503));
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(70));
    probe.emit("fallback");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(2);
    assertThat(collector.exports()).isNotEmpty();
    collector.exports().forEach(value -> assertThat(value.authorization).containsExactly("Bearer dummy-token-A"));
    collector.reset();
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(121));
    assertThat(flowRunner("oauth-business-flow").run().getMessage().getPayload().getValue())
        .isEqualTo("business-success");
    probe.emit("expired");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(3);
    assertThat(collector.exports()).isEmpty();
    probe.emit("cooldown");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(3);
    assertThat(collector.exports()).isEmpty();
    oauth.token("dummy-token-C");
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(125));
    probe.emit("recovered");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(4);
    assertThat(collector.exports()).extracting(value -> value.signal).contains("traces", "metrics", "logs");
    collector.exports().forEach(value -> assertThat(value.authorization).containsExactly("Bearer dummy-token-C"));
  }

  @Test
  public void concurrentSignalFlushesShareOneInFlightTokenRequest() throws Exception {
    java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(3);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(3);
    oauth.blockNextRequest(entered, release);
    probe.emit("concurrent");
    java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
    try {
      for (String accessor : new String[] { "getSdkTracerProvider", "getSdkMeterProvider",
          "getSdkLoggerProvider" }) {
        results.add(workers.submit(() -> {
          ready.countDown();
          try {
            assertThat(start.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            probe.flush(accessor);
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        }));
      }
      assertThat(ready.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      release.countDown();
      for (java.util.concurrent.Future<?> result : results) {
        result.get(6, java.util.concurrent.TimeUnit.SECONDS);
      }
      assertThat(oauth.requests()).isEqualTo(1);
      assertThat(collector.exports()).extracting(value -> value.signal).contains("traces", "metrics", "logs");
    } finally {
      oauth.unblock();
      start.countDown();
      workers.shutdownNow();
      assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  public void malformedResponseRedirectAndTimeoutNeverReachCollector() throws Exception {
    oauth.respond(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"access_token\":\"dummy-secret &\"}"));
    probe.emit("malformed");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(1);
    assertThat(collector.exports()).isEmpty();
    oauth.respond(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(302)
        .withHeader("Location", oauth.tokenUrl().replace(WireMockOAuthServer.PATH, "/redirect-target")));
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
    probe.emit("redirect");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(2);
    oauth.server().verify(0, com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(
        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/redirect-target")));
    assertThat(collector.exports()).isEmpty();
    oauth.respond(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withFixedDelay(3000)
        .withBody("{\"access_token\":\"dummy-token-A\",\"token_type\":\"Bearer\",\"expires_in\":120}"));
    clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
    probe.emit("timeout");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(3);
    assertThat(collector.exports()).isEmpty();
  }

  @Test
  public void stopIsIdempotentAndRecreationAcquiresFreshCredentials() throws Exception {
    probe.emit("beforeStop");
    probe.flush();
    Object oldConnection = config.getOpenTelemetryConnection();
    Object oldSdk = probe.sdk;
    Object oldProvider = probe.provider;
    config.stop();
    config.stop();
    int stoppedRequests = oauth.requests();
    probe.emit("afterStop");
    assertThat(oauth.requests()).isEqualTo(stoppedRequests);
    assertThatThrownBy(() -> OAuthSdkProbe.invoke(oldProvider, "headers")).hasMessageContaining("CLOSED");
    config.start();
    assertThat(config.getOpenTelemetryConnection()).isNotSameAs(oldConnection);
    probe = new OAuthSdkProbe(config.getOpenTelemetryConnection(), clock);
    assertThat(probe.sdk).isNotSameAs(oldSdk);
    assertThat(probe.provider).isNotSameAs(oldProvider);
    collector.reset();
    oauth.token("dummy-token-C");
    probe.emit("fresh");
    probe.flush();
    assertThat(oauth.requests()).isEqualTo(stoppedRequests + 1);
    collector.exports().forEach(value -> assertThat(value.authorization).containsExactly("Bearer dummy-token-C"));
  }

  @Test
  public void tokenTlsDoesNotInheritCollectorTrust() throws Exception {
    probe.emit("untrusted");
    probe.flush();
    assertThat(untrustedOauth.requests()).isZero();
    assertThat(oauth.requests()).isZero();
    assertThat(collector.exports()).isEmpty();
  }

  private void verifyCredentials(int count) {
    com.github.tomakehurst.wiremock.matching.RequestPatternBuilder request = com.github.tomakehurst.wiremock.client.WireMock
        .postRequestedFor(
            com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(WireMockOAuthServer.PATH))
        .withHeader("Content-Type",
            com.github.tomakehurst.wiremock.client.WireMock.containing("application/x-www-form-urlencoded"))
        .withRequestBody(
            com.github.tomakehurst.wiremock.client.WireMock.containing("grant_type=client_credentials"))
        .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
            .containing("scope=telemetry.write+telemetry.read"));
    if ("CLIENT_SECRET_POST".equals(placement())) {
      request.withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.absent())
          .withRequestBody(
              com.github.tomakehurst.wiremock.client.WireMock.containing("client_id=dummy-id%3A%2B"))
          .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
              .containing("client_secret=dummy-secret+%26"));
    } else {
      String encoded = java.util.Base64.getEncoder().encodeToString(
          "dummy-id%3A%2B:dummy-secret+%26".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      request.withHeader("Authorization",
          com.github.tomakehurst.wiremock.client.WireMock.equalTo("Basic " + encoded))
          .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("client_secret="))
          .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("client_id="));
    }
    oauth.server().verify(count, request);
  }
}
