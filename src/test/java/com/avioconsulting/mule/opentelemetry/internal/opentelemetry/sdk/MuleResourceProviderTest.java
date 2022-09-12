package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MuleResourceProviderTest {

  @Test
  public void createResource() {
    Properties props = new Properties();
    props.setProperty("mule.home", "/home/mule");
    props.setProperty("csorganization.id", "MULE_CSORGANIZATION_ID");
    props.setProperty("environment.id", "MULE_ENVIRONMENT_ID");
    props.setProperty("environment.type", "MULE_ENVIRONMENT_TYPE");
    props.setProperty("worker.id", "MULE_WORKER_ID");
    props.setProperty("domain", "MULE_APP_DOMAIN");
    props.setProperty("fullDomain", "MULE_APP_FULL_DOMAIN");
    props.setProperty("application.aws.region", "MULE_APP_AWS_REGION");
    props.setProperty("mule.env", "MULE_ENV");
    props.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
    MuleResource.refresh(); // Ensure the static map is reloaded
    MuleResourceProvider provider = new MuleResourceProvider();
    ConfigProperties configProperties = Mockito.mock(ConfigProperties.class);
    Resource resource = provider.createResource(configProperties);
    assertThat(resource)
        .isNotNull();
    assertThat(resource.getAttributes().asMap())
        .containsEntry(MULE_HOME, "/home/mule")
        .containsEntry(MULE_CSORGANIZATION_ID, "MULE_CSORGANIZATION_ID")
        .containsEntry(MULE_ENVIRONMENT_ID, "MULE_ENVIRONMENT_ID")
        .containsEntry(MULE_ENVIRONMENT_TYPE, "MULE_ENVIRONMENT_TYPE")
        .containsEntry(MULE_WORKER_ID, "MULE_WORKER_ID")
        .containsEntry(MULE_APP_DOMAIN, "MULE_APP_DOMAIN")
        .containsEntry(MULE_APP_FULL_DOMAIN, "MULE_APP_FULL_DOMAIN")
        .containsEntry(MULE_ENVIRONMENT_AWS_REGION, "MULE_APP_AWS_REGION")
        .containsEntry(MULE_ENVIRONMENT_NAME, "MULE_ENV");
    props.forEach((key, value) -> System.clearProperty(key.toString()));
  }
}