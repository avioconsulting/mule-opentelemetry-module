package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
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
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.LocationPart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.MessageProcessorTracingInterceptorFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageProcessorTracingInterceptorFactoryTest extends AbstractInternalTest {

  @Mock
  MuleNotificationProcessor muleNotificationProcessor;

  @Mock
  ConfigurationComponentLocator configurationComponentLocator;

  @Before
  public void setMocks() {
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);
  }

  @Test
  public void get() {
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .get())
                .isInstanceOf(ProcessorTracingInterceptor.class);
  }

  @Test
  public void notInterceptionNonZeroProcessors() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier logger = mock(TypedComponentIdentifier.class);
    ComponentIdentifier loggerIdentifier = mock(ComponentIdentifier.class);
    when(loggerIdentifier.getNamespace()).thenReturn("mule");
    when(loggerIdentifier.getName()).thenReturn("logger");
    when(logger.getIdentifier()).thenReturn(loggerIdentifier);
    when(location.getComponentIdentifier()).thenReturn(logger);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .isFalse();
  }

  private TypedComponentIdentifier getComponentIdentifier(String namespace, String name) {
    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier componentIdentifier = mock(ComponentIdentifier.class);
    if (namespace != null)
      when(componentIdentifier.getNamespace()).thenReturn(namespace);
    if (name != null)
      when(componentIdentifier.getName()).thenReturn(name);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(componentIdentifier);
    return typedComponentIdentifier;
  }

  @Test
  public void interceptDefaultIncludedProcessor() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier("mule", "flow-ref");
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));

    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .isTrue();
  }

  @Test
  public void interceptNotExcludedProcessor() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier("http", "request");
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));

    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .isTrue();
  }

  @Test
  public void doNotInterceptExcludedProcessor() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier("mule", "logger");
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));

    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .isFalse();
  }

  @Test
  public void doNotInterceptWildcardExcludedProcessor() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier("http", "request");
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));

    MuleNotificationProcessor muleNotificationProcessor1 = new MuleNotificationProcessor(null);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    muleNotificationProcessor1.init(connection, new TraceLevelConfiguration(true, Collections.emptyList(),
        Collections.singletonList(new MuleComponent("http", "*")), Collections.emptyList()));

    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor1, configurationComponentLocator)
            .intercept(location))
                .isFalse();
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

    TypedComponentIdentifier componentIdentifier = getComponentIdentifier(null, null);
    when(location.getComponentIdentifier()).thenReturn(componentIdentifier);
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
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
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
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
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .as("Interception before system property")
                .isTrue();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "false");

    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor, configurationComponentLocator)
            .intercept(location))
                .as("Interception after system property")
                .isFalse();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
  }

  @Test
  public void getInterceptExclusions() {
    List<MuleComponent> expected = Arrays.stream(
        "ee,mule,validations,aggregators,json,oauth,scripting,tracing,oauth2-provider,xml,wss,spring,java,avio-logger"
            .split(","))
        .map(s -> new MuleComponent(s, "*")).collect(Collectors.toList());
    assertThat(new MessageProcessorTracingInterceptorFactory(new MuleNotificationProcessor(null),
        configurationComponentLocator)
            .getInterceptExclusions())
                .as("Default Exclusions")
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  public void getInterceptExclusionsWithTraceLevelConfig() {

    List<MuleComponent> expected = Arrays.stream(
        "ee,mule,validations,aggregators,json,oauth,scripting,tracing,oauth2-provider,xml,wss,spring,java,avio-logger"
            .split(","))
        .map(s -> new MuleComponent(s, "*")).collect(Collectors.toList());

    MuleNotificationProcessor muleNotificationProcessor1 = new MuleNotificationProcessor(null);
    muleNotificationProcessor1.init((OpenTelemetryConnection) null,
        new TraceLevelConfiguration(true, Collections.emptyList(),
            Collections.singletonList(new MuleComponent("mule", "logger")), Collections.emptyList()));
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor1, configurationComponentLocator)
            .getInterceptExclusions())
                .as("Default with Trace level Exclusions")
                .isNotEmpty()
                .containsAll(expected)
                .contains(new MuleComponent("mule", "logger"));
  }

  @Test
  public void getInterceptInclusions() {
    assertThat(new MessageProcessorTracingInterceptorFactory(new MuleNotificationProcessor(null),
        configurationComponentLocator)
            .getInterceptInclusions())
                .as("Default Inclusion")
                .isNotEmpty()
                .containsOnly(new MuleComponent("mule", "flow-ref"));
  }

  @Test
  public void getInterceptInclusionsWithTraceLevelConfig() {
    MuleNotificationProcessor muleNotificationProcessor1 = new MuleNotificationProcessor(null);
    muleNotificationProcessor1.init((OpenTelemetryConnection) null,
        new TraceLevelConfiguration(true, Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(new MuleComponent("mule", "logger"))));
    assertThat(
        new MessageProcessorTracingInterceptorFactory(muleNotificationProcessor1, configurationComponentLocator)
            .getInterceptInclusions())
                .as("Default with Trace level Inclusion")
                .isNotEmpty()
                .containsOnly(new MuleComponent("mule", "flow-ref"),
                    new MuleComponent("mule", "logger"));
  }
}