package com.avioconsulting.mule.opentelemetry.internal.util;

import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public class OpenTelemetryUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryUtil.class);

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

  /**
   * This method uses {@link EventContext#getId()} for extracting the unique id
   * for current event processing.
   *
   * @param event
   *            {@link Event} to extract id from
   * @return String id for the current event
   */
  public static String getEventTransactionId(Event event) {
    return getEventTransactionId(event.getContext().getId());
  }

  /**
   * Creates a unique id for current event processing.
   *
   * @param eventId
   *            {@link Event} to extract id from
   * @return String id for the current event
   */
  public static String getEventTransactionId(String eventId) {
    // For child contexts, the primary id is appended with "_{timeInMillis}".
    // We remove time part to get a unique id across the event processing.
    return eventId.split("_")[0];
  }

}
