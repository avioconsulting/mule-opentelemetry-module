package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;

import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.addAttribute;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.isCloudHubV1;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.isCloudHubV2;

/**
 * Provides the semantic attributes of Mule CloudHub as a cloud resource -
 * <a href=
 * "https://opentelemetry.io/docs/specs/semconv/resource/cloud/">OpenTelemetry
 * Cloud Resource Attributes</a>
 */
public class MuleCloudHubResource {

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

  static Resource buildResource() {
    AttributesBuilder builder = Attributes.builder();
    builder.put(CloudIncubatingAttributes.CLOUD_PROVIDER, "mulesoft");
    if (isCloudHubV1()) {
      // Cloudhub v1
      addAttribute("application.aws.region", builder, CloudIncubatingAttributes.CLOUD_REGION);
      builder.put(CloudIncubatingAttributes.CLOUD_PLATFORM, "mulesoft_cloudhub_v1");
      addAttribute("csorganization.id", builder, CloudIncubatingAttributes.CLOUD_ACCOUNT_ID);
    } else if (isCloudHubV2()) {
      // Cloudhub v2
      builder.put(CloudIncubatingAttributes.CLOUD_PLATFORM, "mulesoft_cloudhub_v2");
      addAttribute("csorganization.id", builder, CloudIncubatingAttributes.CLOUD_ACCOUNT_ID);
      addAttribute("ORG_ID", builder, CloudIncubatingAttributes.CLOUD_ACCOUNT_ID);
    }

    return Resource.create(builder.build());
  }

  private MuleCloudHubResource() {
  }
}
