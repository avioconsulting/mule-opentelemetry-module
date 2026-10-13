package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.core.api.el.ExpressionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ProcessorComponentServiceResetTest {

  @Before
  public void resetBeforeTest() {
    ProcessorComponentService._resetForTesting();
  }

  @After
  public void resetAfterTest() {
    ProcessorComponentService._resetForTesting();
  }

  @Test
  public void retainsSingletonAndCachedProviderUntilReset() {
    ProcessorComponentService service = ProcessorComponentService.getInstance();
    ComponentIdentifier identifier = requestIdentifier();
    ComponentRegistryService registry = mock(ComponentRegistryService.class);
    ExpressionManager manager = mock(ExpressionManager.class);
    ProcessorComponent provider = service.getProcessorComponentFor(identifier, manager, registry);

    assertThat(ProcessorComponentService.getInstance()).isSameAs(service);
    assertThat(service.getProcessorComponentFor(identifier, manager, registry)).isSameAs(provider);
  }

  @Test
  public void resetReplacesBothServiceAndProviderInstances() {
    ProcessorComponentService firstService = ProcessorComponentService.getInstance();
    ComponentIdentifier identifier = requestIdentifier();
    ProcessorComponent firstProvider = firstService.getProcessorComponentFor(identifier,
        mock(ExpressionManager.class), mock(ComponentRegistryService.class));

    ProcessorComponentService._resetForTesting();

    ProcessorComponentService secondService = ProcessorComponentService.getInstance();
    ProcessorComponent secondProvider = secondService.getProcessorComponentFor(identifier,
        mock(ExpressionManager.class), mock(ComponentRegistryService.class));
    assertThat(secondService).isNotSameAs(firstService);
    assertThat(firstProvider).isNotNull();
    assertThat(secondProvider).isNotNull().isNotSameAs(firstProvider);
  }

  private ComponentIdentifier requestIdentifier() {
    return ComponentIdentifier.builder().namespace("http").name("request").build();
  }
}
