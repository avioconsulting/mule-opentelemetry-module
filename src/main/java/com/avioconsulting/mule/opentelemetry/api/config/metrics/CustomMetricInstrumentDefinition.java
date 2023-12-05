package com.avioconsulting.mule.opentelemetry.api.config.metrics;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.List;
import java.util.Objects;

/**
 * Instrument definition for capturing custom metrics.ß
 */
@Alias("Metric Instrument")
public class CustomMetricInstrumentDefinition {

  @Parameter
  @Placement(order = 1)
  @Summary("Name of the Metric Instrument. See OpenTelemetry Semantic Conventions for <a href='https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/metrics.md#instrument-naming'>Instrument Naming Guidelines</a>")
  @Example("org.business.orders.count")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private String metricName;

  @Parameter
  @Placement(order = 2)
  @Optional(defaultValue = "COUNTER")
  @DisplayName("Instrument Type")
  @Summary("Type of the instrument to use such as Counter, Histogram, Gauge etc.")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private MetricsInstrumentType instrumentType;

  @Parameter
  @Placement(order = 3)
  @Summary("Description of the Metric Instrument")
  @Example("Count the number of orders received")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private String description;

  @Parameter
  @Placement(order = 4)
  @Summary("Unit of the Metric Instrument")
  @Example("1")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private String unit;
  @Parameter
  @Placement(order = 5)
  @Optional
  @Summary("List of the attribute keys that can be added to this metric value")
  @Example("org.business.order.channel.source")
  @NullSafe
  @Alias("attribute-keys")
  private List<String> attributeKeys;

  public String getMetricName() {
    return metricName;
  }

  public CustomMetricInstrumentDefinition setMetricName(String metricName) {
    this.metricName = metricName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public String getUnit() {
    return unit;
  }

  public List<String> getAttributeKeys() {
    return attributeKeys;
  }

  public MetricsInstrumentType getInstrumentType() {
    return instrumentType;
  }

  public CustomMetricInstrumentDefinition setInstrumentType(MetricsInstrumentType instrumentType) {
    this.instrumentType = instrumentType;
    return this;
  }

  public CustomMetricInstrumentDefinition setDescription(String description) {
    this.description = description;
    return this;
  }

  public CustomMetricInstrumentDefinition setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  public CustomMetricInstrumentDefinition setAttributeKeys(List<String> attributeKeys) {
    this.attributeKeys = attributeKeys;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    CustomMetricInstrumentDefinition that = (CustomMetricInstrumentDefinition) o;
    return Objects.equals(getMetricName(), that.getMetricName())
        && Objects.equals(getDescription(), that.getDescription())
        && Objects.equals(getUnit(), that.getUnit()) && getInstrumentType() == that.getInstrumentType()
        && Objects.equals(getAttributeKeys(), that.getAttributeKeys());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMetricName(), getDescription(), getUnit(), getInstrumentType(), getAttributeKeys());
  }
}
