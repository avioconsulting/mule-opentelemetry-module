package com.avioconsulting.mule.opentelemetry.internal.processor;

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
import static org.mockito.Mockito.when;

public abstract class AbstractProcessorComponentTest {

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

    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier mockedIdentifier = getMockedIdentifier(namespace, name);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(mockedIdentifier);
    when(componentLocation.getComponentIdentifier()).thenReturn(typedComponentIdentifier);

    return componentLocation;
  }

}
