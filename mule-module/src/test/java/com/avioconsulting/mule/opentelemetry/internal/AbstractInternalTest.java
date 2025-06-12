package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.junit.Before;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractInternalTest {

  @Before
  public void setupTests() {
    OpenTelemetryConnection.resetForTest();
  }

  protected TypedComponentIdentifier getComponentIdentifier(String namespace, String name) {
    TypedComponentIdentifier typedComponentIdentifier = mock(TypedComponentIdentifier.class);
    ComponentIdentifier componentIdentifier = mock(ComponentIdentifier.class);
    if (namespace != null)
      when(componentIdentifier.getNamespace()).thenReturn(namespace);
    if (name != null)
      when(componentIdentifier.getName()).thenReturn(name);
    when(typedComponentIdentifier.getIdentifier()).thenReturn(componentIdentifier);
    return typedComponentIdentifier;
  }

}
