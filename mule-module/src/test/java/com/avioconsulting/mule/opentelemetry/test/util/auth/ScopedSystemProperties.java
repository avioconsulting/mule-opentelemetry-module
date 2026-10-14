package com.avioconsulting.mule.opentelemetry.test.util.auth;

import java.util.HashMap;
import java.util.Map;
import org.junit.rules.ExternalResource;

/**
 * Restores overrides after all @After methods, including Mule context disposal.
 */
public final class ScopedSystemProperties extends ExternalResource {
  private final Map<String, String> previous = new HashMap<>();

  public void remember(String key) {
    if (!previous.containsKey(key)) {
      previous.put(key, System.getProperty(key));
    }
  }

  public void set(String key, String value) {
    remember(key);
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  @Override
  protected void after() {
    previous.forEach((key, value) -> {
      if (value == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, value);
      }
    });
    previous.clear();
  }
}
