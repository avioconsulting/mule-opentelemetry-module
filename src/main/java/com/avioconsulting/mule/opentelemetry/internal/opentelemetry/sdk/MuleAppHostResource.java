package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOGGER = LoggerFactory.getLogger(MuleAppHostResource.class);

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
      Resource hostResource = HostResource.get();
      String workerPublicIP = getProperty("worker.publicIP");
      String hostname = hostResource.getAttribute(HOST_NAME);
      if (workerPublicIP != null) {
        attributes.put(HostIncubatingAttributes.HOST_IP, workerPublicIP);
        hostname = "ip-" + workerPublicIP.replace(".", "-");
      }
      String hostStrategy = config.getString("mule.otel.service.host.chv1.strategy", "");
      switch (hostStrategy) {
        case "service_name":
          hostname = config.getString("otel.service.name");
          break;
        case "env_id":
          hostname = getProperty("environment.id");
          break;
        case "":
          break;
        default:
          LOGGER.warn("Invalid mule.otel.service.host.chv1.strategy value: {}", hostStrategy);
      }
      if (hostname != null) {
        attributes.put(HOST_NAME, hostname);
      }
      String workerId = getProperty("worker.id", "na");
      String container = String.format("%s-%s", getProperty("domain"), workerId);
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
