package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.semconv.SemanticAttributes;

import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.addAttribute;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.getProperty;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.isCloudHubV1;
import static com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil.isCloudHubV2;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_REGION;

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
    builder.put(ResourceAttributes.CLOUD_PROVIDER, "mulesoft");
    if (isCloudHubV1()) {
      // Cloudhub v1
      addAttribute("application.aws.region", builder, CLOUD_REGION);
      builder.put(ResourceAttributes.CLOUD_PLATFORM, "mulesoft_cloudhub_v1");
      addAttribute("csorganization.id", builder, CLOUD_ACCOUNT_ID);
    } else if (isCloudHubV2()) {
      // Cloudhub v2
      builder.put(ResourceAttributes.CLOUD_PLATFORM, "mulesoft_cloudhub_v2");
      addAttribute("ORG_ID", builder, CLOUD_ACCOUNT_ID);
    }

    return Resource.create(builder.build(), SemanticAttributes.SCHEMA_URL);
  }

  private MuleCloudHubResource() {
  }
}
