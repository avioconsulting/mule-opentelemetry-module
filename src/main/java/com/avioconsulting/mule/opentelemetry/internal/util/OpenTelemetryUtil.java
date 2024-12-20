package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

  /**
   * If given system property exists, this will set its value as an attribute to
   * provided {@link AttributesBuilder}.
   *
   * @param property
   *            Name of the system property to search for.
   * @param builder
   *            {@link AttributesBuilder} instance to add attribute
   * @param attributeKey
   *            {@link AttributeKey} to use for setting attribute in given
   *            {@link AttributesBuilder}
   */
  public static void addAttribute(String property, AttributesBuilder builder,
      AttributeKey<String> attributeKey) {
    String value = PropertiesUtil.getProperty(property);
    if (value != null) {
      builder.put(attributeKey, value);
    }
  }

  /**
   * Resolves any expressions in the TraceComponent's spanName and tags using the
   * provided ExpressionManager
   * based on the given Event.
   *
   * @param traceComponent
   *            the TraceComponent containing spanName and tags to resolve
   * @param expressionManager
   *            the ExpressionManager used to evaluate expressions
   * @param event
   *            the Event used for context in expression evaluation
   */
  public static void resolveExpressions(TraceComponent traceComponent, ExpressionManager expressionManager,
      Event event) {
    try {
      if (expressionManager
          .isExpression(traceComponent.getSpanName())) {
        TypedValue<String> evaluatedSpanName = (TypedValue<String>) expressionManager
            .evaluate(traceComponent.getSpanName(), event.asBindingContext());
        if (evaluatedSpanName.getValue() != null) {
          traceComponent.withSpanName(evaluatedSpanName.getValue());
        }
      }
      List<Map.Entry<String, String>> expressionTags = traceComponent.getTags().entrySet().stream()
          .filter(e -> expressionManager.isExpression(e.getValue())).collect(Collectors.toList());
      for (Map.Entry<String, String> expressionTag : expressionTags) {
        TypedValue<String> evaluate = (TypedValue<String>) expressionManager.evaluate(expressionTag.getValue(),
            event.asBindingContext());
        if (evaluate.getValue() != null) {
          traceComponent.getTags().replace(expressionTag.getKey(), evaluate.getValue());
        }
      }
    } catch (Exception ignored) {
    }
  }
}
