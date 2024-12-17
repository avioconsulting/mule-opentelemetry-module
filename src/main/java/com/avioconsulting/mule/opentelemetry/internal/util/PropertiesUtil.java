package com.avioconsulting.mule.opentelemetry.internal.util;

import java.util.Locale;

public class PropertiesUtil {
  public static final String MULE_OTEL_USE_APIKIT_SPAN_NAMES = "mule.otel.use.apikit.span.names";

  /**
   * Should APIKit Flow names be used to name http root spans? Default true.
   */
  private static boolean useAPIKitSpanNames = true;

  private PropertiesUtil() {
  }

  static {
    init();
  }

  public static void init() {
    String useAPIKitSpanNames = getProperty(MULE_OTEL_USE_APIKIT_SPAN_NAMES);
    if (useAPIKitSpanNames != null) {
      PropertiesUtil.useAPIKitSpanNames = Boolean.parseBoolean(useAPIKitSpanNames);
    }
  }

  public static String getProperty(String name) {
    return getProperty(name, null);
  }

  public static String getProperty(String name, String defaultValue) {
    if (name == null)
      return null;
    String value = System.getProperty(name);
    if (value == null) {
      value = System.getenv(toEnvName(name));
    }
    if (value == null) {
      value = defaultValue;
    }
    return value;
  }

  public static boolean getBoolean(String name, boolean defaultValue) {
    String property = getProperty(name);
    if (property == null)
      return defaultValue;
    return Boolean.parseBoolean(property);
  }

  private static String toEnvName(String propertyName) {
    return propertyName.toUpperCase(Locale.ROOT).replaceAll("\\.", "_")
        .replaceAll("-", "_");
  }

  public static boolean isCloudHubV1() {
    return getProperty("fullDomain") != null;
  }

  public static boolean isCloudHubV2() {
    return getProperty("NODE_NAME") != null;
  }

  public static boolean isUseAPIKitSpanNames() {
    return useAPIKitSpanNames;
  }
}
