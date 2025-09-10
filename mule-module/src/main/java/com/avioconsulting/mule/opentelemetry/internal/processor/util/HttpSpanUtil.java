package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import com.avioconsulting.mule.opentelemetry.api.traces.Taggable;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.FunctionMemoizer;
import io.opentelemetry.semconv.HttpAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_FLOW_NAME;

public class HttpSpanUtil {

  // Pre-compute common HTTP methods (already uppercase)
  private static final Map<String, String> HTTP_METHODS = new HashMap<>(10, 1.0f);
  private static final ConcurrentHashMap<String, String> HTTP_SPAN_NAME_CACHE = new ConcurrentHashMap<>();
  static {
    HTTP_METHODS.put("connect", "CONNECT");
    HTTP_METHODS.put("delete", "DELETE");
    HTTP_METHODS.put("get", "GET");
    HTTP_METHODS.put("head", "HEAD");
    HTTP_METHODS.put("options", "OPTIONS");
    HTTP_METHODS.put("patch", "PATCH");
    HTTP_METHODS.put("post", "POST");
    HTTP_METHODS.put("put", "PUT");
    HTTP_METHODS.put("trace", "TRACE");
  }

  /**
   * Get HTTP Method name with either old and new Semantic Attribute for Method.
   * 
   * @param tags
   *            {@link Map} containing span tags
   * @return String HTTP Method name
   */
  public static String method(Taggable<String, String> tags) {
    return Objects.requireNonNull(tags.getTag(HttpAttributes.HTTP_REQUEST_METHOD.getKey()),
        "HTTP Method must not be null");
  }

  /**
   * Generates HTTP Span name
   * 
   * @param tags
   *            {@link Map} createTraceComponent Span tags with HTTP Method entry
   * @param route
   *            {@link String} HTTP Route path
   * @return String span name
   */
  public static String spanName(Taggable<String, String> tags, String route) {
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
    String key = method + route;
    return HTTP_SPAN_NAME_CACHE.computeIfAbsent(key, k -> {
      if (HTTP_METHODS.containsKey(method)) {
        return HTTP_METHODS.get(method) + " " + route;
      } else {
        return method.toUpperCase() + " " + route;
      }
    });
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
  public static String apiKitRoutePath(Taggable<String, String> tags) {
    String flowName = tags.getTag(MULE_APP_FLOW_NAME.getKey());
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
