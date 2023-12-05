package com.avioconsulting.mule.opentelemetry.internal.operations;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.values.ValueBuilder;
import org.mule.runtime.extension.api.values.ValueProvider;
import org.mule.runtime.extension.api.values.ValueResolvingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CustomMetricAttributeValueProvider implements ValueProvider {

  @Config
  OpenTelemetryExtensionConfiguration configuration;

  @Parameter
  private String metricName;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    System.out.println("Getting values for " + metricName);
    List<String> attributes = new ArrayList<>();
    if (configuration != null
        && !configuration.getCustomMetricInstruments().isEmpty()) {
      attributes = configuration.getCustomMetricInstruments().stream()
          .filter(instrument -> metricName.equals(instrument.getMetricName()))
          .findFirst()
          .map(CustomMetricInstrumentDefinition::getAttributeKeys)
          .orElse(Collections.emptyList());
    }
    return ValueBuilder.getValuesFor(attributes);
  }
}
