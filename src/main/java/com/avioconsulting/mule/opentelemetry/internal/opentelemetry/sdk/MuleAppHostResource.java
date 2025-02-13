package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;

import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.addAttribute;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.getProperty;
import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_ID;
import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_NAME;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;

/**
 * Provides Host and Container Resource attributes based on deployment target -
 * CloudHub v1, CloudHub v2, or Other
 */
public final class MuleAppHostResource {

  private static Resource INSTANCE = buildResource();

  public static Resource get() {
    return INSTANCE;
  }

  /**
   * Used to refresh the loaded properties. Currently used by tests to reset the
   * instances between testing.
   */
  public static void refresh() {
    INSTANCE = buildResource();
  }

  private static Resource buildResource() {
    AttributesBuilder attributes = Attributes.builder();
    if (PropertiesUtil.isCloudHubV2()) {
      // Cloudhub V2
      Resource containerResource = ContainerResource.get();
      addAttribute("NODE_NAME", attributes, HOST_NAME);
      attributes.putAll(containerResource.getAttributes());
      addAttribute("HOSTNAME", attributes, CONTAINER_NAME);
    } else if (PropertiesUtil.isCloudHubV1()) {
      // Cloudhub V1
      addAttribute("environment.id", attributes, HOST_NAME);
      String workerId = getProperty("worker.id", "na");
      String container = String.format("%s-%s", getProperty("domain"), workerId);
      if (getProperty("worker.publicIP") != null) {
        attributes.put(HostIncubatingAttributes.HOST_IP, getProperty("worker.publicIP"));
      }
      attributes.put(CONTAINER_NAME, container);
      attributes.put(CONTAINER_ID, container);
    } else {
      // Default Host attributes
      Resource hostResource = HostResource.get();
      attributes.putAll(hostResource.getAttributes());
    }
    return Resource.create(attributes.build());
  }

  private MuleAppHostResource() {
  }
}
