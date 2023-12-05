package com.avioconsulting.mule.opentelemetry.api.config.metrics;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MetricsInstrumentType {

  // Counter - when value is monotonically increasing
  COUNTER("counter");

  private final String value;
  private static Map<String, MetricsInstrumentType> instrumentTypes;

  MetricsInstrumentType(String value) {
    this.value = value;
  }

  static {
    instrumentTypes = Arrays.stream(MetricsInstrumentType.values())
        .collect(Collectors.toMap(MetricsInstrumentType::getValue, Function.identity()));
  }

  public String getValue() {
    return value;
  }

  MetricsInstrumentType fromValue(String value) {
    return instrumentTypes.get(value);
  }
}
