package com.avioconsulting.mule.opentelemetry.internal.processor;

import org.junit.Test;
import org.mule.runtime.api.component.ComponentIdentifier;

import static org.assertj.core.api.Assertions.assertThat;

public class MuleCoreProcessorComponentTest extends AbstractProcessorComponentTest {

  @Test
  public void getNamespace() {
    assertThat(new MuleCoreProcessorComponent().getNamespace()).isEqualTo("mule");
  }

  @Test
  public void getOperations() {
    assertThat(new MuleCoreProcessorComponent().getOperations()).contains("flow-ref");
  }

  @Test
  public void getSources() {
    assertThat(new MuleCoreProcessorComponent().getSources()).isEmpty();
  }

  @Test
  public void canHandleFlowRef() {
    ComponentIdentifier identifier = getMockedIdentifier("mule", "flow-ref");
    assertThat(new MuleCoreProcessorComponent().canHandle(identifier)).isTrue();
  }

}