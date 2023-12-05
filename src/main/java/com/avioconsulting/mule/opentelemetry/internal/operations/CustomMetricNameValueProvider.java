package com.avioconsulting.mule.opentelemetry.internal.operations;

import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.values.ValueBuilder;
import org.mule.runtime.extension.api.values.ValueProvider;
import org.mule.runtime.extension.api.values.ValueResolvingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Value provider for Custom Metric name based on the global configuration
 */
public class CustomMetricNameValueProvider implements ValueProvider {
  @Config
  OpenTelemetryExtensionConfiguration configuration;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    List<String> attributes = new ArrayList<>();
    if (configuration != null
        && !configuration.getCustomMetricInstruments().isEmpty()) {
      configuration.getCustomMetricInstruments()
          .forEach(instrument -> attributes.add(instrument.getMetricName()));
    }
    return ValueBuilder.getValuesFor(attributes);
  }
}
