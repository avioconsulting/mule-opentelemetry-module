package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Properties;

import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_PLATFORM;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_PROVIDER;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_REGION;
import static io.opentelemetry.semconv.ResourceAttributes.CONTAINER_ID;
import static io.opentelemetry.semconv.ResourceAttributes.CONTAINER_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.HOST_IP;
import static io.opentelemetry.semconv.ResourceAttributes.HOST_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class MuleAppHostResourceProviderTest {

  @Test
  public void get_cloudhub_v1() {
    Properties props = new Properties();
    props.setProperty("fullDomain", "test.fullDomain");
    props.setProperty("domain", "test");
    props.setProperty("environment.id", "test-env-id");
    props.setProperty("worker.id", "0");
    props.setProperty("worker.publicIP", "30.40.50.60");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleAppHostResource.refresh();
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(4)
        .containsEntry(HOST_NAME, "test-env-id")
        .containsEntry(HOST_IP, Arrays.asList("30.40.50.60"))
        .containsEntry(CONTAINER_ID, "test-0")
        .containsEntry(CONTAINER_NAME, "test-0");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

  @Test
  public void get_cloudhub_v2() {
    Properties props = new Properties();
    props.setProperty("NODE_NAME", "ip-10-0-0-1.some.host");
    props.setProperty("HOSTNAME", "test-host-name");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleAppHostResource.refresh();
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    // TODO: Mock CGGROUP so container.id is populated
    assertThat(resource.getAttributes().asMap())
        .hasSize(2)
        .containsEntry(HOST_NAME, "ip-10-0-0-1.some.host")
        .containsEntry(CONTAINER_NAME, "test-host-name");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

  @Test
  public void get_no_cloudhub() {
    System.clearProperty("NODE_NAME");
    System.clearProperty("fullDomain");
    MuleAppHostResource.refresh();
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(2)
        .containsAllEntriesOf(HostResource.get().getAttributes().asMap());
  }

}