package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.LocationPart;
import org.mule.runtime.api.event.Event;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY;
import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InterceptorProcessorConfigTest extends AbstractInternalTest {

  private @NotNull ComponentLocation getLocation(String namespace, String name) {
    return getLocationAt(namespace, name, "anything-but-0");
  }

  private @NotNull ComponentLocation getLocationAtZero(String namespace, String name) {
    return getLocationAt(namespace, name, "0");
  }

  private @NotNull ComponentLocation getLocationAt(String namespace, String name, String locationEndPath) {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/" + locationEndPath);

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier(namespace, name);
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    return location;
  }

  private Event event = getEvent();

  @Test
  public void do_not_intercept_wildcard_excluded_processors() {

    ComponentLocation location = getLocation("http", "request");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.singletonList(new MuleComponent("http", "*")), Collections.emptyList());

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isFalse();

  }

  @Test
  public void do_not_intercept_explicit_excluded_processors() {

    ComponentLocation location = getLocation("http", "request");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.singletonList(new MuleComponent("http", "request")), Collections.emptyList());

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isFalse();

  }

  @Test
  public void intercept_wildcard_excluded_but_individual_included_processors() {

    ComponentLocation location = getLocation("http", "request");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.singletonList(new MuleComponent("http", "*")),
        Collections.singletonList(new MuleComponent("http", "request")));

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isTrue();

  }

  @Test
  public void intercept_wildcard_included_processors() {

    ComponentLocation location = getLocation("some", "processor");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(new MuleComponent("some", "*")));

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isTrue();

  }

  @Test
  public void intercept_explicit_included_processors() {

    ComponentLocation location = getLocation("some", "processor");
    ComponentLocation notIncludedLocation = getLocation("some", "diff-processor");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(new MuleComponent("some", "processor")));

    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isTrue();
    assertThat(
        interceptorProcessorConfig.shouldIntercept(notIncludedLocation, event))
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
    TypedComponentIdentifier componentIdentifier = getComponentIdentifier(null, null);
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    assertThat(
        new InterceptorProcessorConfig().shouldIntercept(location, event))
            .as("Interception before system property")
            .isTrue();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "false");

    assertThat(
        new InterceptorProcessorConfig().shouldIntercept(location, event))
            .as("Interception after system property")
            .isFalse();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
  }

  @Test
  public void do_not_intercept_anything_that_is_not_configured_and_processor_0() {
    ComponentLocation location = getLocation("mule", "logger");
    assertThat(
        new InterceptorProcessorConfig().shouldIntercept(location, event))
            .isFalse();
  }

  @Test
  public void intercept_processor_0() {
    ComponentLocation location = getLocationAtZero("mule", "logger");
    assertThat(
        new InterceptorProcessorConfig().shouldIntercept(location, event))
            .isTrue();
  }

  @Test
  public void do_not_intercept_included_processors_without_processor_zero_enabled() {

    ComponentLocation location = getLocation("some", "processor");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(new MuleComponent("some", "processor")));
    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .as("Processor is enabled to intercept and processor zero is disabled")
            .isTrue();

    // Enable the first processor only mode
    System.setProperty(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY, "true");
    InterceptorProcessorConfig interceptorProcessorConfigWithout0 = new InterceptorProcessorConfig();
    interceptorProcessorConfigWithout0.updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfigWithout0.shouldIntercept(location, event))
            .as("Processor is enabled to intercept and processor zero is enabled")
            .isFalse();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY);
  }

  @Test
  public void interception_disabled_from_global_config_turnoff_flag() {
    ComponentLocation location = getLocation("some", "processor");

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.emptyList(), Collections.singletonList(new MuleComponent("some", "processor")));
    InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();
    interceptorProcessorConfig
        .updateTraceConfiguration(traceLevelConfiguration);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isTrue();

    // Turn off tracing
    interceptorProcessorConfig
        .setTurnOffTracing(true);

    assertThat(
        interceptorProcessorConfig.shouldIntercept(location, event))
            .isFalse();
  }

}