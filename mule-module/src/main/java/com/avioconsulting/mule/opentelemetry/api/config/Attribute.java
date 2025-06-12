package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.Alias;

@Alias("attribute")
public class Attribute extends KeyValuePair {

  public Attribute() {
    super();
  }

  public Attribute(String key, String value) {
    super(key, value);
  }
}
