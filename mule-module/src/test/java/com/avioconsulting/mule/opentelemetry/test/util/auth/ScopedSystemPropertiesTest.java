package com.avioconsulting.mule.opentelemetry.test.util.auth;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import static org.assertj.core.api.Assertions.*;

public class ScopedSystemPropertiesTest {
  private static final String EXPORTER = "otel.metrics.exporter";
  private static final String ENDPOINT = "oauth.property.isolation.endpoint";

  @Test
  public void restoresOriginalExporterAndAbsentPropertiesAfterSuperclassTeardown() {
    verifyRestoration(SuccessfulFixture.class, true);
  }

  @Test
  public void restoresPropertiesEvenWhenSetupFails() {
    verifyRestoration(FailingFixture.class, false);
  }

  private void verifyRestoration(Class<?> fixture, boolean success) {
    String exporter = System.getProperty(EXPORTER);
    String endpoint = System.getProperty(ENDPOINT);
    try {
      System.setProperty(EXPORTER, "none");
      System.clearProperty(ENDPOINT);
      Result result = JUnitCore.runClasses(fixture);
      assertThat(result.wasSuccessful()).isEqualTo(success);
      assertThat(System.getProperty(EXPORTER)).isEqualTo("none");
      assertThat(System.getProperty(ENDPOINT)).isNull();
    } finally {
      restore(EXPORTER, exporter);
      restore(ENDPOINT, endpoint);
    }
  }

  private static void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  public static class ParentFixture {
    @After
    public void disposeContext() {
      // Reproduce a superclass teardown changing properties after a subclass @After.
      System.setProperty(EXPORTER, "otlp");
    }
  }

  public static class SuccessfulFixture extends ParentFixture {
    @Rule
    public final ScopedSystemProperties properties = new ScopedSystemProperties();

    @org.junit.Before
    public void configureOAuth() {
      properties.set(EXPORTER, "otlp");
      properties.set(ENDPOINT, "https://localhost:12345");
    }

    @Test
    public void exports() {
      assertThat(System.getProperty(EXPORTER)).isEqualTo("otlp");
    }
  }

  public static class FailingFixture extends SuccessfulFixture {
    @Override
    public void configureOAuth() {
      super.configureOAuth();
      throw new IllegalStateException("Simulated Mule startup failure");
    }
  }
}
