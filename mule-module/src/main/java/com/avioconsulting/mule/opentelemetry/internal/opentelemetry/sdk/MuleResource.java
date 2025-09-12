package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.resources.ProcessResource;
import io.opentelemetry.sdk.resources.Resource;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.addAttribute;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.*;

/**
 * Creates an OpenTelemetry {@link Resource} that adds Mule Runtime specific
 * attributes in trace data.
 */
public class MuleResource {
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

  /**
   * Builds Mule Resource instance by extracting Mule related system properties.
   * 
   * @return {@link Resource} populated with mule attributes
   */
  private static Resource buildResource() {
    AttributesBuilder builder = Attributes.builder();
    addAttribute("mule.home", builder, MULE_HOME);
    addAttribute("csorganization.id", builder, MULE_CSORGANIZATION_ID);
    addAttribute("csorganization.id", builder, MULE_ORGANIZATION_ID);
    addAttribute("environment.id", builder, MULE_ENVIRONMENT_ID);
    addAttribute("environment.type", builder, MULE_ENVIRONMENT_TYPE);
    addAttribute("worker.id", builder, MULE_WORKER_ID);
    addAttribute("domain", builder, MULE_APP_DOMAIN);
    addAttribute("fullDomain", builder, MULE_APP_FULL_DOMAIN);
    addAttribute("application.aws.region", builder, MULE_ENVIRONMENT_AWS_REGION);
    addMuleProcessAttributes(builder);
    Attributes build = builder.build();
    return Resource.create(build);
  }

  private static void addMuleProcessAttributes(AttributesBuilder builder) {
    // ProcessResource has been disabled to avoid sending sensitive data possibly
    // present in command line strings
    // and to reduce the size of attributes processed. See OpenTelemetryConnection
    // initialization for disabled resource providers
    // We are only adding Process Id and Executable Path for Mule here
    Resource resource = ProcessResource.get();
    Long pid = resource.getAttribute(PROCESS_PID);
    if (pid != null && pid >= 0) {
      builder.put(PROCESS_PID, pid);
    }
    String executablePath = resource.getAttribute(PROCESS_EXECUTABLE_PATH);
    if (executablePath != null && !executablePath.isEmpty()) {
      builder.put(PROCESS_EXECUTABLE_PATH, executablePath);
    }
  }

  private MuleResource() {

  }
}
