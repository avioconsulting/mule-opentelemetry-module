package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.Alias;

@Alias("header")
public class Header extends KeyValuePair {

  public Header() {
    super();
  }

  public Header(String key, String value) {
    super(key, value);
  }
}
