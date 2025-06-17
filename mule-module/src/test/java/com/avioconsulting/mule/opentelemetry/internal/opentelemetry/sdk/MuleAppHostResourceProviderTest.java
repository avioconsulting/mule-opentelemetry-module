package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Properties;

import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_ID;
import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_NAME;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_IP;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MuleAppHostResourceProviderTest {

  private ConfigProperties configProperties;

  @Before
  public void setup() {
    this.configProperties = mock(ConfigProperties.class);
    when(configProperties.getString(eq("otel.service.name"))).thenReturn("test-app");
    when(configProperties.getString("mule.otel.service.host.chv1.strategy", "")).thenReturn("");
  }

  @Test
  public void get_cloudhub_v1_default_host() {
    Properties props = new Properties();
    props.setProperty("fullDomain", "test.fullDomain");
    props.setProperty("domain", "test");
    props.setProperty("environment.id", "test-env-id");
    props.setProperty("worker.id", "0");
    props.setProperty("worker.publicIP", "30.40.50.60");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleAppHostResource.refresh(configProperties);
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(4)
        .containsEntry(HOST_NAME, "ip-30-40-50-60")
        .containsEntry(HOST_IP, Arrays.asList("30.40.50.60"))
        .containsEntry(CONTAINER_ID, "test-0")
        .containsEntry(CONTAINER_NAME, "test-0");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

  @Test
  public void get_cloudhub_v1_host_override_service_name() {
    Properties props = new Properties();
    props.setProperty("fullDomain", "test.fullDomain");
    props.setProperty("domain", "test");
    props.setProperty("environment.id", "test-env-id");
    props.setProperty("worker.id", "0");
    props.setProperty("worker.publicIP", "30.40.50.60");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    when(configProperties.getString("mule.otel.service.host.chv1.strategy", "")).thenReturn("service_name");
    MuleAppHostResource.refresh(configProperties);
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(4)
        .containsEntry(HOST_NAME, "test-app")
        .containsEntry(HOST_IP, Arrays.asList("30.40.50.60"))
        .containsEntry(CONTAINER_ID, "test-0")
        .containsEntry(CONTAINER_NAME, "test-0");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }

  @Test
  public void get_cloudhub_v1_host_override_env_id() {
    Properties props = new Properties();
    props.setProperty("fullDomain", "test.fullDomain");
    props.setProperty("domain", "test");
    props.setProperty("environment.id", "test-env-id");
    props.setProperty("worker.id", "0");
    props.setProperty("worker.publicIP", "30.40.50.60");

    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    when(configProperties.getString("mule.otel.service.host.chv1.strategy", "")).thenReturn("env_id");
    MuleAppHostResource.refresh(configProperties);
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
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
    MuleAppHostResource.refresh(configProperties);
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
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
    MuleAppHostResource.refresh(configProperties);
    MuleAppHostResourceProvider provider = new MuleAppHostResourceProvider();
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .hasSize(2)
        .containsAllEntriesOf(HostResource.get().getAttributes().asMap());
  }

}