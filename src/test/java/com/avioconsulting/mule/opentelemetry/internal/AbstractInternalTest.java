package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.junit.Before;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractInternalTest {

  @Before
  public void setupTests() {
    OpenTelemetryConnection.resetForTest();
  }

  protected Event getEvent() {
    return getEvent(UUID.randomUUID().toString());
  }

  protected Event getEvent(String correlationId) {
    EventContext mockContext = mock(EventContext.class);
    when(mockContext.getId()).thenReturn(UUID.randomUUID().toString());
    when(mockContext.getCorrelationId()).thenReturn(correlationId);

    Event mock = mock(Event.class);
    when(mock.getCorrelationId()).thenReturn(correlationId);
    when(mock.getContext()).thenReturn(mockContext);
    return mock;
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
