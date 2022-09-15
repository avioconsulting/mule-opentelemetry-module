package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.Objects;

public class MuleComponent {

  @Parameter
  @Summary("Mule Component Namespace")
  @Example("mule")
  private String namespace;

  @Parameter
  @Summary("Mule Component Name")
  @Example("logger")
  private String name;

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public MuleComponent() {

  }

  public MuleComponent(String namespace, String name) {
    this.namespace = namespace;
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    MuleComponent that = (MuleComponent) o;
    return getNamespace().equals(that.getNamespace()) && getName().equals(that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getNamespace(), getName());
  }

  @Override
  public String toString() {
    return getNamespace().concat(":").concat(getName());
  }
}
