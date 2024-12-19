package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

public class MuleCloudHubResourceProvider implements ResourceProvider {
  @Override
  public Resource createResource(ConfigProperties config) {
    return MuleCloudHubResource.get();
  }

}
