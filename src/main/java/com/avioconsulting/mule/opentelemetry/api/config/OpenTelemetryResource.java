package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.List;
import java.util.Objects;

@Alias("resource")
public class OpenTelemetryResource {

  @Parameter
  @Summary("Service name for this application.")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private String serviceName;

  public String getServiceName() {
    return serviceName;
  }

  @Parameter
  @Optional
  @NullSafe
  private List<Attribute> resourceAttributes;

  public List<Attribute> getResourceAttributes() {
    return resourceAttributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    OpenTelemetryResource that = (OpenTelemetryResource) o;
    return Objects.equals(serviceName, that.serviceName)
        && Objects.equals(resourceAttributes, that.resourceAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceName, resourceAttributes);
  }
}
