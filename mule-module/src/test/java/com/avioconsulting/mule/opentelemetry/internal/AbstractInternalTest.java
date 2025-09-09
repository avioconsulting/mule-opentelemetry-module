package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.util.TraceComponentManager;
import com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil;
import org.junit.Before;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractInternalTest {

  protected DefaultComponentLocation.DefaultLocationPart rootFlowPart = new DefaultComponentLocation.DefaultLocationPart(
      "MyFlow",
      Optional.of(TypedComponentIdentifier.builder()
          .type(TypedComponentIdentifier.ComponentType.FLOW)
          .identifier(ComponentIdentifier.buildFromStringRepresentation("flow"))
          .build()),
      Optional.empty(),
      OptionalInt.empty(),
      OptionalInt.empty());
  protected DefaultComponentLocation.DefaultLocationPart processorsPart = new DefaultComponentLocation.DefaultLocationPart(
      "processors",
      Optional.empty(),
      Optional.empty(),
      OptionalInt.empty(),
      OptionalInt.empty());

  protected DefaultComponentLocation.DefaultLocationPart processors_0_Part = new DefaultComponentLocation.DefaultLocationPart(
      "0",
      Optional.empty(),
      Optional.empty(),
      OptionalInt.empty(),
      OptionalInt.empty());

  protected TypedComponentIdentifier getComponentIdentifier(String namespace, String name,
      TypedComponentIdentifier.ComponentType type) {
    return TypedComponentIdentifier.builder()
        .identifier(ComponentIdentifier.buildFromStringRepresentation(namespace + ":" + name))
        .type(type).build();
  }

  @Before
  public void setupTests() {
    OpenTelemetryConnection._resetForTest();
    BatchHelperUtil._resetForTesting();
    TraceComponentManager.resetForTest();
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
