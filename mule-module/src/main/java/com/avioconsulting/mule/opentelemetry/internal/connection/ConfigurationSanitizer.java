package com.avioconsulting.mule.opentelemetry.internal.connection;

import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostics intentionally reveal property presence, never property values.
 */
public final class ConfigurationSanitizer {
  private ConfigurationSanitizer() {
  }

  public static Map<String, String> sanitize(Map<String, String> properties) {
    Map<String, String> safe = new TreeMap<>();
    properties.forEach(
        (key, value) -> safe.put(key.matches("otel\\.[a-z0-9_.-]+") ? key : "<custom-property>", "<redacted>"));
    return safe;
  }
}
