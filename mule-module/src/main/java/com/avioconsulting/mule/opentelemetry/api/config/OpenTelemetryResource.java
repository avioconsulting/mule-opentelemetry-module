package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.*;

@Alias("resource")
public class OpenTelemetryResource implements OtelConfigMapProvider {

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

  public OpenTelemetryResource() {

  }

  public OpenTelemetryResource(String serviceName, List<Attribute> resourceAttributes) {
    this.serviceName = serviceName;
    this.resourceAttributes = resourceAttributes;
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

  @Override
  public Map<String, String> getConfigMap() {
    Map<String, String> configMap = new HashMap<>();
    if (getServiceName() != null) {
      configMap.put("otel.service.name", getServiceName());
    }
    configMap.put("otel.resource.attributes",
        KeyValuePair
            .commaSeparatedList(getResourceAttributes()));
    return Collections.unmodifiableMap(configMap);
  }
}
