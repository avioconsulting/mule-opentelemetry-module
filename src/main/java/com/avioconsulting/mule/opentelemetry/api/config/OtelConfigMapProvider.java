package com.avioconsulting.mule.opentelemetry.api.config;

import java.util.Map;

/**
 * OpenTelemetry Configuration Map provider interface.
 *
 * See {@link OpenTelemetryResource}, {@link SpanProcessorConfiguration}
 */
public interface OtelConfigMapProvider {

  /**
   * The implementation of {@link #getConfigMap()} must return a
   * {@link Map}
   * with OpenTelemetry semantic attribute keys and appropriate values.
   * 
   * @return {@link Map}
   */
  Map<String, String> getConfigMap();
}
