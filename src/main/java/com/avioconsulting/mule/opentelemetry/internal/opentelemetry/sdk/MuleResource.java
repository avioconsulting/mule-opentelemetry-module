package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;

/**
 * Creates an OpenTelemetry @{@link Resource} that adds Mule Runtime specific
 * attributes in trace data.
 */
public class MuleResource {
  private static final Resource INSTANCE = buildResource();

  public static Resource get() {
    return INSTANCE;
  }

  private static Resource buildResource() {
    AttributesBuilder builder = Attributes.builder();
    addAttribute("mule.home", builder, MULE_HOME);
    addAttribute("csorganization.id", builder, MULE_CSORGANIZATION_ID);
    addAttribute("environment.id", builder, MULE_ENVIRONMENT_ID);
    addAttribute("environment.type", builder, MULE_ENVIRONMENT_TYPE);
    addAttribute("worker.id", builder, MULE_WORKER_ID);
    addAttribute("domain", builder, MULE_APP_DOMAIN);
    addAttribute("fullDomain", builder, MULE_APP_FULL_DOMAIN);
    addAttribute("application.aws.region", builder, MULE_ENVIRONMENT_AWS_REGION);
    addAttribute("mule.env", builder, MULE_ENVIRONMENT_NAME);
    Attributes build = builder.build();
    return Resource.create(build);
  }

  private static void addAttribute(String key, AttributesBuilder builder,
      AttributeKey<String> attributeKey) {
    String value = System.getProperty(key);
    if (value != null) {
      builder.put(attributeKey, value);
    }
  }

  private MuleResource() {

  }
}
