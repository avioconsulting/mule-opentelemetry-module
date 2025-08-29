package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.FunctionMemoizer;
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

  private final static FunctionMemoizer<String, String> apiKitRoutePathExtractorMemoizer = new FunctionMemoizer<>(
      HttpSpanUtil::apiKitRoutePathExtractor);

  /**
   * Extracts and transforms the APIKit route path from the flow name.
   * Format: action:\path[:content-type][:config-name]
   *
   * @param tags
   *            Map containing MULE_APP_FLOW_NAME
   * @return Normalized path (e.g., "/accounts/{accountId}")
   */
  public static String apiKitRoutePath(Map<String, String> tags) {
    String flowName = tags.get(MULE_APP_FLOW_NAME.getKey());
    return apiKitRoutePathExtractorMemoizer.apply(flowName);
  }

  private static String apiKitRoutePathExtractor(String flowName) {
    Objects.requireNonNull(flowName, "Flow name must not be null");
    // Extract path boundaries (after action, before optional content-type)
    int pathStart = flowName.indexOf(':') + 1;
    if (pathStart == 0) {
      throw new RuntimeException("Invalid flow name format: " + flowName);
    }

    // End where next colon for content type or config name is
    int pathEnd = flowName.indexOf(':', pathStart);
    if (pathEnd == -1) {
      pathEnd = flowName.length();
    }

    StringBuilder result = new StringBuilder(pathEnd - pathStart);

    for (int i = pathStart; i < pathEnd; i++) {
      char c = flowName.charAt(i);
      switch (c) {
        case '\\':
          result.append('/');
          break;
        case '(':
          result.append('{');
          break;
        case ')':
          result.append('}');
          break;
        default:
          result.append(c);
          break;
      }
    }

    return result.toString();
  }
}
