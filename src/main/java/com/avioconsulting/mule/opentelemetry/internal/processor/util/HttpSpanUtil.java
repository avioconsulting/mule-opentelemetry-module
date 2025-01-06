package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.semconv.HttpAttributes;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_FLOW_NAME;

public class HttpSpanUtil {

  /**
   * Get HTTP Method name with either old and new Semantic Attribute for Method.
   * 
   * @param tags
   *            {@link Map} containing span tags
   * @return String HTTP Method name
   */
  public static String method(Map<String, String> tags) {
    String method = tags.get(HttpAttributes.HTTP_REQUEST_METHOD.getKey());
    Objects.requireNonNull(method, "HTTP Method must not be null");
    return method;
  }

  /**
   * Generates HTTP Span name
   * 
   * @param tags
   *            {@link Map} of Span tags with HTTP Method entry
   * @param route
   *            {@link String} HTTP Route path
   * @return String span name
   */
  public static String spanName(Map<String, String> tags, String route) {
    return spanName(method(tags), route);
  }

  /**
   * Generates HTTP Span name
   * 
   * @param method
   *            {@link String} HTTP Method name
   * @param route
   *            {@link String} HTTP Route path
   * @return String span name
   */
  public static String spanName(String method, String route) {
    if (!PropertiesUtil.isUseAPIKitSpanNames()) {
      // Backward compatible to use old route naming without method names
      return route;
    }
    return method.toUpperCase(Locale.ROOT) + " " + route;
  }

  /**
   * Uses flow names to build a new span name
   * 
   * @param tags
   *            {@link Map} containing span tags
   * @param rootSpanName
   *            {@link String}
   * @return String name of the span using apikit route path
   */
  public static String apiKitRoutePath(Map<String, String> tags, String rootSpanName) {
    Objects.requireNonNull(rootSpanName, "Root span name must not be null");
    String flowName = tags.get(MULE_APP_FLOW_NAME.getKey());
    Objects.requireNonNull(flowName, "Flow name must not be null");
    String pathName = (flowName.split(":")[1]).replace(":", "")
        .replaceAll("\\\\", "/")
        .replaceAll("\\(", "{").replaceAll("\\)", "}");
    return rootSpanName.replace("/*", pathName);
  }
}
