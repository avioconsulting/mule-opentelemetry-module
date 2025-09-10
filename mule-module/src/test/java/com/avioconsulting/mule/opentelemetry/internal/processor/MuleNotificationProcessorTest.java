package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.notification.MessageProcessorNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class MuleNotificationProcessorTest extends AbstractProcessorComponentTest {

  ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);

  @Test
  public void handleProcessorStartEvent_withDisabledProcessorSpans() {

    MessageProcessorNotification notification = getMessageProcessorNotification();
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleProcessorStartEvent(notification);
    verify(connection).isTurnOffTracing();
    verify(connection).getExpressionManager();
    verifyNoMoreInteractions(connection);
  }

  @Test
  public void handleProcessorStartEvent_withSkippedNamedProcessorSpans() {

    MessageProcessorNotification notification = getMessageProcessorNotification();
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);

    List<MuleComponent> skippedProcessors = new ArrayList<>();
    skippedProcessors.add(new MuleComponent("mule", "logger"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, skippedProcessors);

    notificationProcessor.init(connection, traceLevelConfiguration);
    notificationProcessor.handleProcessorStartEvent(notification);
    verify(connection).isTurnOffTracing();
    verifyNoMoreInteractions(connection);
  }

  @Test
  public void handleProcessorStartEvent_withSkipProcessorSpansByNamespace() {
    MessageProcessorNotification notification = getMessageProcessorNotification();
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);

    List<MuleComponent> skippedProcessors = new ArrayList<>();
    skippedProcessors.add(new MuleComponent("mule", "*"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, skippedProcessors);

    notificationProcessor.init(connection, traceLevelConfiguration);
    notificationProcessor.handleProcessorStartEvent(notification);
    verify(connection).isTurnOffTracing();
    verifyNoMoreInteractions(connection);
  }

  @Test
  public void handleProcessorEndEvent_withDisabledProcessorSpans() {

    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    Message message = getMessage(null);
    when(event.getMessage()).thenReturn(message);
    ComponentLocation componentLocation = getComponentLocation("mule", "logger");
    Component component = getComponent(componentLocation, Collections.emptyMap(), "mule", "logger");
    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleProcessorEndEvent(notification);
    verify(connection).isTurnOffTracing();
    verify(connection).getExpressionManager();
    verifyNoMoreInteractions(connection);
  }

  @Test
  public void getProcessorComponent_additional_span_components() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    Message message = getMessage(null);
    when(event.getMessage()).thenReturn(message);
    ComponentLocation componentLocation = getComponentLocation("mule", "remove-variable");
    Component component = getComponent(componentLocation, Collections.emptyMap(), "mule", "remove-variable");

    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);

    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(false, Collections.emptyList(),
        Collections.emptyList());
    notificationProcessor.init(connection, traceLevelConfiguration);

    ProcessorComponent processorComponent = notificationProcessor.getProcessorComponent(component.getIdentifier());
    Assertions.assertThat(processorComponent).as("Processor without the additional list to create span").isNull();

    // New notification processor to clear any cached values
    notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);
    List<MuleComponent> additionalSpans = new ArrayList<>();
    additionalSpans.add(new MuleComponent("mule", "remove-variable"));
    traceLevelConfiguration = new TraceLevelConfiguration(false, Collections.emptyList(), additionalSpans);
    notificationProcessor.init(connection, traceLevelConfiguration);
    processorComponent = notificationProcessor.getProcessorComponent(component.getIdentifier());
    Assertions.assertThat(processorComponent).as("Processor with the additional list to create span").isNotNull()
        .isInstanceOf(GenericProcessorComponent.class);

  }

  @Test
  public void handleProcessorStartEvent_doesNotPropagateException() {

    MessageProcessorNotification notification = getMessageProcessorNotification();
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    when(connection.getExpressionManager()).thenReturn(null); // cause NPE

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);
    notificationProcessor.init(connection, new TraceLevelConfiguration(true, Collections.emptyList()));

    Throwable any = Assertions.catchThrowable(
        () -> notificationProcessor.handleProcessorStartEvent(notification));

    Assertions.assertThat(any).as("An exception was not thrown").isNull();

  }

  @NotNull
  private MessageProcessorNotification getMessageProcessorNotification() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    Message message = getMessage(null);
    when(event.getMessage()).thenReturn(message);
    EventContext context = mock(EventContext.class);
    when(context.getId()).thenReturn(UUID.randomUUID().toString());
    when(event.getContext()).thenReturn(context);
    ComponentLocation componentLocation = getComponentLocation("mule", "logger");
    Component component = getComponent(componentLocation, Collections.emptyMap(), "mule", "logger");
    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);
    return notification;
  }

}