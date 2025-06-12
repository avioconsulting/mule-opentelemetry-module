package com.avioconsulting.mule.opentelemetry.api.config;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Collections;

public class OpenTelemetryResourceTest {

  @Test
  public void getResourceConfigMap() {
    OpenTelemetryResource resource = new OpenTelemetryResource("test-svc-name",
        Collections.singletonList(new Attribute("key", "value")));
    Assertions.assertThat(resource.getConfigMap())
        .as("Resource configuration map")
        .containsEntry("otel.service.name", "test-svc-name")
        .containsEntry("otel.resource.attributes", "key=value");
  }
}