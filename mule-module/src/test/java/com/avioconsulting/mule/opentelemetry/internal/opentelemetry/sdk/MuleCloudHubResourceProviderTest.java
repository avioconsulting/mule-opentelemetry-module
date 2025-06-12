package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;

import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_PLATFORM;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_PROVIDER;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_REGION;
import static org.assertj.core.api.Assertions.assertThat;

public class MuleCloudHubResourceProviderTest {

  @Test
  public void get_cloudhub_v1() {
    Properties props = new Properties();
    props.setProperty("fullDomain", "testDomain");
    props.setProperty("csorganization.id", "test-org-id");
    props.setProperty("application.aws.region", "us-west-2");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleCloudHubResource.refresh();
    MuleCloudHubResourceProvider provider = new MuleCloudHubResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(4)
        .containsEntry(CLOUD_PROVIDER, "mulesoft")
        .containsEntry(CLOUD_REGION, "us-west-2")
        .containsEntry(CLOUD_PLATFORM, "mulesoft_cloudhub_v1")
        .containsEntry(CLOUD_ACCOUNT_ID, "test-org-id");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

  @Test
  public void get_cloudhub_v2() {
    Properties props = new Properties();
    props.setProperty("NODE_NAME", "ip-10-0-0-1.some.host");
    props.setProperty("ORG_ID", "test-org-id");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleCloudHubResource.refresh();
    MuleCloudHubResourceProvider provider = new MuleCloudHubResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(3)
        .containsEntry(CLOUD_PROVIDER, "mulesoft")
        .containsEntry(CLOUD_PLATFORM, "mulesoft_cloudhub_v2")
        .containsEntry(CLOUD_ACCOUNT_ID, "test-org-id");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

}