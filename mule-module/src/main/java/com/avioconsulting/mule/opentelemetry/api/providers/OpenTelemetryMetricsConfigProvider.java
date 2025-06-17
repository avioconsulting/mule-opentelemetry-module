package com.avioconsulting.mule.opentelemetry.api.providers;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;

public interface OpenTelemetryMetricsConfigProvider {

  /**
   * This method will be called before initializing the
   * OpenTelemetry instance.
   * It provides an opportunity to change the OpenTelemetry behavior. Please make
   * sure only metrics related behavior
   * is modified.
   */
  void initialise(AppIdentifier appIdentifier);

  /**
   * This method is called after OpenTelemetry is
   * instantiated.
   */
  void start();

  /**
   * This method is called while disposing the
   * OpenTelemetry instance.
   */
  void stop();

}
