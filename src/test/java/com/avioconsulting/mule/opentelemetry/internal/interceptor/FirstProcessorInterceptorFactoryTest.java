package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.component.location.ComponentLocation;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.FirstProcessorInterceptorFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

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
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isFalse();
  }

  @Test
  public void interceptionDefaultEnabled() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("MyFlow/processors/0");
    assertThat(new FirstProcessorInterceptorFactory(muleNotificationProcessor).intercept(location))
        .isTrue();
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
    when(location.getLocation()).thenReturn("MyFlow/processors/0");
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