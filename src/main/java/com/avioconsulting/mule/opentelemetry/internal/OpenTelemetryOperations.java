package com.avioconsulting.mule.opentelemetry.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import org.mule.runtime.extension.api.annotation.param.MediaType;

/**
 * This class is a container for operations, every public method in this class
 * will be taken as an
 * extension operation.
 */
public class OpenTelemetryOperations {

  /**
   * Example of a simple operation that receives a string parameter and returns a
   * new string
   * message that will be set on the payload.
   */
  @MediaType(value = ANY, strict = false)
  public String sayHi(String person) {
    return buildHelloMessage(person);
  }

  /** Private Methods are not exposed as operations */
  private String buildHelloMessage(String person) {
    return "Hello " + person + "!!!";
  }
}
