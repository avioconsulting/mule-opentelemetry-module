package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class KeyValuePair {

  @Parameter
  private String key;

  @Parameter
  private String value;

  public KeyValuePair() {
  }

  public KeyValuePair(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public KeyValuePair setKey(String key) {
    this.key = key;
    return this;
  }

  public String getValue() {
    return value;
  }

  public KeyValuePair setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    KeyValuePair that = (KeyValuePair) o;
    return Objects.equals(key, that.key) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public String toString() {
    return key + "=" + value;
  }

  public static String commaSeparatedList(List<? extends KeyValuePair> pairs) {
    if (pairs == null)
      return "";
    return pairs.stream().map(KeyValuePair::toString).collect(Collectors.joining(","));
  }
}
