package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule;
import org.junit.ClassRule;
import org.mockito.Mockito;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;

import javax.xml.namespace.QName;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public abstract class AbstractProcessorComponentTest extends AbstractInternalTest {

  @ClassRule
  public static OpenTelemetryRule openTelemetryRule = OpenTelemetryRule.create();

  /**
   * Retrieves an OpenTelemetry connection with the specified configuration
   * settings.
   *
   * Returns a {@link org.mockito.Spy} of created {@link OpenTelemetryConnection}
   * so that additional mock behavior can
   * be set by the caller.
   *
   * @return a Spy of OpenTelemetryConnection instance with the specified
   *         configuration settings
   */
  protected OpenTelemetryConnection getOpenTelemetryConnection() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);
    when(configuration.isTurnOffMetrics()).thenReturn(true);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);
    OpenTelemetryConnection spy = spy(instance);
    spy.withOpenTelemetry(openTelemetryRule.getOpenTelemetry());
    return spy;
  }

  protected ComponentIdentifier getMockedIdentifier(String namespace, String name) {
    ComponentIdentifier identifier = mock(ComponentIdentifier.class);
    Mockito.when(identifier.getNamespace()).thenReturn(namespace);
    Mockito.when(identifier.getName()).thenReturn(name);
    return identifier;
  }

  /**
   *
   * @param componentLocation
   * @param componentParameters
   *            Map of parameters like name, config-ref, doc:name etc that are on
   *            component xml element.
   * @return
   */
  protected Component getComponent(ComponentLocation componentLocation, Map<String, String> componentParameters) {
    return getComponent(componentLocation, componentParameters, "mule", "random");
  }

  /**
   *
   * @param componentLocation
   * @param componentParameters
   *            Map of parameters like name, config-ref, doc:name etc that are on
   *            component xml element.
   * @param namespace
   * @param name
   * @return
   */
  protected Component getComponent(ComponentLocation componentLocation, Map<String, String> componentParameters,
      String namespace, String name) {
    Component component = mock(Component.class);
    when(component.getLocation()).thenReturn(componentLocation);
    ComponentIdentifier mockedIdentifier = getMockedIdentifier(namespace, name);
    when(component.getIdentifier()).thenReturn(mockedIdentifier);
    when(component.getAnnotation(QName.valueOf("{config}componentParameters"))).thenReturn(componentParameters);
    return component;
  }

  protected Error getError(Object attributes) {
    Error error = mock(Error.class);
    when(error.getDescription()).thenReturn("Something failed");
    Message message = getMessage(attributes);
    when(error.getErrorMessage()).thenReturn(message);
    return error;
  }

  protected Message getMessage(Object attributes) {
    Message message = mock(Message.class);
    when(message.getAttributes()).thenReturn(TypedValue.of(attributes));
    return message;
  }

  protected ComponentLocation getComponentLocation() {
    return getComponentLocation("mule", "random");
  }

  protected ComponentLocation getComponentLocation(String namespace, String name) {
    ComponentLocation componentLocation = mock(ComponentLocation.class);
    when(componentLocation.getLocation()).thenReturn("test/processors/0");
    when(componentLocation.getRootContainerName()).thenReturn("test-flow");

    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier mockedIdentifier = getMockedIdentifier(namespace, name);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(mockedIdentifier);
    when(componentLocation.getComponentIdentifier()).thenReturn(typedComponentIdentifier);

    return componentLocation;
  }

  protected ComponentLocation getFlowLocation(String flowName) {
    ComponentLocation componentLocation = mock(ComponentLocation.class);
    when(componentLocation.getLocation()).thenReturn(flowName);
    when(componentLocation.getRootContainerName()).thenReturn(flowName);

    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier mockedIdentifier = getMockedIdentifier("mule", "flow");
    when(typedComponentIdentifier.getIdentifier()).thenReturn(mockedIdentifier);
    when(componentLocation.getComponentIdentifier()).thenReturn(typedComponentIdentifier);

    return componentLocation;
  }

}
