package com.avioconsulting.mule.opentelemetry.internal;

import java.util.Map;
import java.util.Objects;

public class OpenTelemetryUtil {

  /**
   * <pre>
   * Extract any attributes defined via system properties (see {@link System#getProperties()}) for provided <code>configName</code>.
   *
   * It uses `{configName}.otel.{attributeKey}` pattern to identify relevant system properties. Key matching is case-insensitive.
   * </pre>
   *
   * @param configName
   *            {@link String} name of the component's global configuration
   *            element
   * @param tags
   *            Modifiable {@link Map} to populate any
   * @param sourceMap
   *            {@link Map} contains all properties to search in
   *
   */
  public static void addGlobalConfigSystemAttributes(String configName, Map<String, String> tags,
      Map<String, String> sourceMap) {
    if (configName == null || configName.trim().isEmpty())
      return;
    Objects.requireNonNull(tags, "Tags map cannot be null");
    String configRef = configName.toLowerCase();
    String replaceVal = configRef + ".otel.";
    sourceMap.entrySet().stream().filter(e -> e.getKey().startsWith(configRef)).forEach(entry -> {
      String propKey = entry.getKey().substring(replaceVal.length());
      tags.put(propKey, entry.getValue());
    });
  }

}
