package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.LocationPart;

import java.util.Arrays;
import java.util.Optional;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY;
import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ProcessorTracingInterceptorFactoryTest extends AbstractInternalTest {

  @Mock
  MuleNotificationProcessor muleNotificationProcessor;

  @Before
  public void setup() {
    when(muleNotificationProcessor.getInterceptorProcessorConfig()).thenReturn(new InterceptorProcessorConfig());
  }

  @Test
  public void get() {
    assertThat(
        new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
            .get())
                .isInstanceOf(ProcessorTracingInterceptor.class);
  }

  private static LocationPart getLocationPart(String path) {
    LocationPart part = mock(LocationPart.class);
    doReturn(part).when(part).getPartPath();
    return part;
  }

  @Test
  public void interceptionDisabledBySystemProperty() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/0");
    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(
        new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
            .intercept(location))
                .as("Interception before system property")
                .isTrue();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "false");
    // Create a new InterceptorProcessorConfig so the sys prop is picked up
    when(muleNotificationProcessor.getInterceptorProcessorConfig()).thenReturn(new InterceptorProcessorConfig());
    try {

      assertThat(
          new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
              .intercept(location))
                  .as("Interception after system property")
                  .isFalse();

    } finally {
      System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
    }

  }

  @Test
  public void interceptOnlyProcessor0_NotDefaultIncluded() {
    // When the first processor only interception is enabled,
    // any location other than processor 0 must not be intercepted

    ComponentLocation processor0 = Mockito.mock(ComponentLocation.class);
    when(processor0.getRootContainerName()).thenReturn("MyFlow");
    when(processor0.getLocation()).thenReturn("MyFlow/processors/0");

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(processor0.getParts()).thenReturn(Arrays.asList(part1));

    assertThat(
        new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
            .intercept(processor0))
                .isTrue();

    ComponentLocation flowRefLocation = Mockito.mock(ComponentLocation.class);
    when(flowRefLocation.getRootContainerName()).thenReturn("MyFlow");
    when(flowRefLocation.getLocation()).thenReturn("MyFlow/processors/anything-but-0");
    LocationPart flowRefPart = mock(LocationPart.class);
    TypedComponentIdentifier identifier2 = mock(TypedComponentIdentifier.class);
    when(identifier2.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(flowRefPart.getPartIdentifier()).thenReturn(Optional.of(identifier2));
    when(flowRefLocation.getParts()).thenReturn(Arrays.asList(flowRefPart));

    System.setProperty(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY, "true");
    when(muleNotificationProcessor.getInterceptorProcessorConfig()).thenReturn(new InterceptorProcessorConfig());
    try {
      assertThat(
          new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
              .intercept(flowRefLocation))
                  .isFalse();
    } finally {
      System.clearProperty(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY);
    }

  }

  @Test
  public void interceptProcessor0AndDefaultIncluded() {
    // When interception is not disabled, the processor 0 and others are intercepted
    // Default property value is false, so not setting it.
    // System.setProperty(MULE_OTEL_FIRST_PROCESSOR_INTERCEPTOR_ONLY, "false");

    ComponentLocation processor0 = Mockito.mock(ComponentLocation.class);
    when(processor0.getRootContainerName()).thenReturn("MyFlow");
    when(processor0.getLocation()).thenReturn("MyFlow/processors/0");

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(processor0.getParts()).thenReturn(Arrays.asList(part1));

    assertThat(
        new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
            .intercept(processor0))
                .isTrue();

    ComponentLocation flowRefLocation = Mockito.mock(ComponentLocation.class);
    when(flowRefLocation.getRootContainerName()).thenReturn("MyFlow");
    when(flowRefLocation.getLocation()).thenReturn("MyFlow/processors/anything-but-0");
    TypedComponentIdentifier muleFlowRefIdentifier = getComponentIdentifier("mule", "flow-ref");
    when(flowRefLocation.getComponentIdentifier()).thenReturn(muleFlowRefIdentifier);
    LocationPart flowRefPart = mock(LocationPart.class);
    TypedComponentIdentifier identifier2 = mock(TypedComponentIdentifier.class);
    when(identifier2.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(flowRefPart.getPartIdentifier()).thenReturn(Optional.of(identifier2));
    when(flowRefLocation.getParts()).thenReturn(Arrays.asList(flowRefPart));

    assertThat(
        new ProcessorTracingInterceptorFactory(muleNotificationProcessor)
            .intercept(flowRefLocation))
                .isTrue();

  }
}