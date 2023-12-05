package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;
import io.opentelemetry.api.common.AttributeKey;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds an initialized metric instrument.
 * 
 * @param <I>
 *            Type of the instrument such as
 *            {@link io.opentelemetry.api.metrics.LongCounter}
 */
public class CustomMetricInstrumentHolder<I> {
  private CustomMetricInstrumentDefinition metricInstrument;
  private I instrument;
  private Map<String, AttributeKey<String>> attributeKeys = new HashMap<>();

  public CustomMetricInstrumentDefinition getMetricInstrument() {
    return metricInstrument;
  }

  public CustomMetricInstrumentHolder<I> setMetricInstrument(CustomMetricInstrumentDefinition metricInstrument) {
    this.metricInstrument = metricInstrument;
    attributeKeys = metricInstrument.getAttributeKeys().stream().map(AttributeKey::stringKey)
        .collect(Collectors.toMap(AttributeKey::getKey, Function.identity()));
    return this;
  }

  public I getInstrument() {
    return instrument;
  }

  public CustomMetricInstrumentHolder<I> setInstrument(I instrument) {
    this.instrument = instrument;
    return this;
  }

  public Map<String, AttributeKey<String>> getAttributeKeys() {
    return attributeKeys;
  }
}
