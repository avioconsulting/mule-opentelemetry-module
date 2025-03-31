package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
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

  private static Resource INSTANCE = null;

  public static Resource get(ConfigProperties config) {
    return getInternal(config);
  }

  private static synchronized Resource getInternal(ConfigProperties config) {
    if (INSTANCE != null)
      return INSTANCE;
    INSTANCE = buildResource(config);
    return INSTANCE;
  }

  /**
   * Used to refresh the loaded properties. Currently used by tests to reset the
   * instances between testing.
   */
  public static void refresh(ConfigProperties configProperties) {
    INSTANCE = buildResource(configProperties);
  }

  private static Resource buildResource(ConfigProperties config) {
    AttributesBuilder attributes = Attributes.builder();
    if (PropertiesUtil.isCloudHubV2()) {
      // Cloudhub V2
      Resource containerResource = ContainerResource.get();
      addAttribute("NODE_NAME", attributes, HOST_NAME);
      attributes.putAll(containerResource.getAttributes());
      addAttribute("HOSTNAME", attributes, CONTAINER_NAME);
    } else if (PropertiesUtil.isCloudHubV1()) {
      // Cloudhub V1
      boolean useEnvIdHost = config.getBoolean("mule.otel.service.host.chv1.env_id", false);
      String hostname = useEnvIdHost ? getProperty("environment.id")
          : config.getString("otel.service.name");
      if (hostname != null) {
        attributes.put(HOST_NAME, hostname);
      }
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
