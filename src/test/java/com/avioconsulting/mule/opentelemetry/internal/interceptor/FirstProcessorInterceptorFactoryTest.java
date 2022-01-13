package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.junit.Test;
import org.mockito.Mockito;
import org.mule.runtime.api.component.location.ComponentLocation;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.FirstProcessorInterceptorFactory.*;
import static org.assertj.core.api.Assertions.assertThat;

public class FirstProcessorInterceptorFactoryTest {

  @Test
  public void get() {
    assertThat(new FirstProcessorInterceptorFactory().get())
        .isInstanceOf(ProcessorTracingInterceptor.class);
  }

  @Test
  public void notInterceptionNonZeroProcessors() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    Mockito.when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");
    assertThat(new FirstProcessorInterceptorFactory().intercept(location))
        .isFalse();
  }

  @Test
  public void interceptionDefaultEnabled() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    Mockito.when(location.getLocation()).thenReturn("MyFlow/processors/0");
    assertThat(new FirstProcessorInterceptorFactory().intercept(location))
        .isTrue();
  }

  @Test
  public void interceptionDisabledBySystemProperty() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    Mockito.when(location.getLocation()).thenReturn("MyFlow/processors/0");
    assertThat(new FirstProcessorInterceptorFactory().intercept(location))
        .as("Interception before system property")
        .isTrue();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "false");

    assertThat(new FirstProcessorInterceptorFactory().intercept(location))
        .as("Interception after system property")
        .isFalse();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
  }
}