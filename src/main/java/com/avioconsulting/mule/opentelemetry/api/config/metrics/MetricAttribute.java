package com.avioconsulting.mule.opentelemetry.api.config.metrics;

import com.avioconsulting.mule.opentelemetry.internal.operations.CustomMetricAttributeValueProvider;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.values.OfValues;

import java.util.Objects;

public class MetricAttribute {

  @Parameter
  @OfValues(value = CustomMetricAttributeValueProvider.class, open = false)
  private String key;

  @Parameter
  private String value;

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    MetricAttribute that = (MetricAttribute) o;
    return Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getKey(), getValue());
  }
}
