package com.avioconsulting.mule.opentelemetry.api.providers;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import org.mule.runtime.extension.api.annotation.Alias;

@Alias("No Metrics")
public class NoopOpenTelemetryMetricsConfigProvider implements OpenTelemetryMetricsConfigProvider {

  @Override
  public void initialise(AppIdentifier appIdentifier) {
    // nothing to do here
  }

  @Override
  public void start() {
    // nothing to do here
  }

  @Override
  public void stop() {
    // nothing to do here
  }

}
