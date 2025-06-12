package com.avioconsulting.mule.opentelemetry.api.providers;

/**
 * When contributing a metrics configuration extension to this module, this
 * exported supplier interface
 * can help acquire instance of metrics provider configuration in extensions.
 * This should be implemented by classes annotated
 * with @{@link org.mule.runtime.extension.api.annotation.Configuration}.
 * Example use case, acquiring this in operations implemented by the extension.
 */
public interface OpenTelemetryMetricsConfigSupplier {

  /**
   * Get {@link OpenTelemetryMetricsConfigProvider} instance associated with the
   * configuration.
   * 
   * @return OpenTelemetryMetricsConfigProvider
   */
  OpenTelemetryMetricsConfigProvider getMetricsConfigProvider();
}
