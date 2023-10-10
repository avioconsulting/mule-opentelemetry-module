package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.processor.HttpProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.LocationPart;

import java.util.Arrays;
import java.util.Optional;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.FirstProcessorInterceptorFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FirstProcessorInterceptorFactoryTest {

  @Mock
  MuleNotificationProcessor muleNotificationProcessor;

  @Before
  public void setMocks() {
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);
  }

  @Test
  public void get() {
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).get())
        .isInstanceOf(ProcessorTracingInterceptor.class);
  }

  @Test
  public void notInterceptionNonZeroProcessors() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier logger = mock(TypedComponentIdentifier.class);
    ComponentIdentifier loggerIdentifier = mock(ComponentIdentifier.class);
    when(loggerIdentifier.getName()).thenReturn("logger");
    when(logger.getIdentifier()).thenReturn(loggerIdentifier);
    when(location.getComponentIdentifier()).thenReturn(logger);

    when(muleNotificationProcessor.getProcessorComponent(loggerIdentifier)).thenReturn(Optional.empty());

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isFalse();
  }

  @Test
  public void interceptWithKnownProcessor() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier httpRequest = mock(TypedComponentIdentifier.class);
    ComponentIdentifier requestIdentifier = mock(ComponentIdentifier.class);
    when(httpRequest.getIdentifier()).thenReturn(requestIdentifier);
    when(location.getComponentIdentifier()).thenReturn(httpRequest);

    when(muleNotificationProcessor.getProcessorComponent(requestIdentifier))
        .thenReturn(Optional.of(new HttpProcessorComponent()));

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isTrue();
  }

  @Test
  public void interceptionDefaultEnabled() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/0");
    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isTrue();
  }

  private static LocationPart getLocationPart(String path) {
    LocationPart part = mock(LocationPart.class);
    doReturn(part).when(part).getPartPath();
    return part;
  }

  @Test
  public void interceptionDisabledDueToNotificationListener() {
    reset(muleNotificationProcessor);
    when(muleNotificationProcessor.hasConnection()).thenReturn(false);
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isFalse();
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
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .as("Interception before system property")
        .isTrue();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "false");

    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .as("Interception after system property")
        .isFalse();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
  }

}