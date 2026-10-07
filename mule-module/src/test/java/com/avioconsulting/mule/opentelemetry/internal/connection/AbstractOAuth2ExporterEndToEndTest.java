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
  protected LocalOtlpCollector collector;
  protected OpenTelemetryExtensionConfiguration config;
  protected OAuthSdkProbe probe;
  protected final AtomicLong clock = new AtomicLong();
  private final Map<String, String> previous = new HashMap<>();

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
      collector = new LocalOtlpCollector(protocol(), tls);
      set("javax.net.ssl.trustStore", AuthenticationTlsFixture.keyStorePath());
      set("javax.net.ssl.trustStorePassword", AuthenticationTlsFixture.PASSWORD);
      set("javax.net.ssl.trustStoreType", "PKCS12");
      set("oauth.e2e.collector", collector.endpoint());
      set("oauth.e2e.protocol", protocol().name());
      set("oauth.e2e.tokenUrl", oauth.tokenUrl());
      set("oauth.e2e.certificate", tls.certificatePath());
      set("oauth.e2e.key", tls.privateKeyPath());
      set("oauth.e2e.placement", placement());
      set("oauth.e2e.timeout", "8000");
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
    collector.exports().forEach(value -> {
      assertThat(value.authorization).containsExactly("Bearer dummy-token-A");
      assertThat(value.tenant).containsExactly("dummy-tenant");
      assertThat(value.compressed).isTrue();
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
