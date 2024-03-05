package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.notification.MessageProcessorNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

public class MuleNotificationProcessorTest extends AbstractProcessorComponentTest {

  private ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);

  @Test
  public void handleProcessorStartEvent_withDisabledProcessorSpans() {

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
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(configurationComponentLocator);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleProcessorStartEvent(notification);
    verifyNoMoreInteractions(connection);
  }

  @Test
  public void handleProcessorStartEvent_withSkippedNamedProcessorSpans() {

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
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(configurationComponentLocator);

    List<MuleComponent> skippedProcessors = new ArrayList<>();
    skippedProcessors.add(new MuleComponent("mule", "logger"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, skippedProcessors);

    notificationProcessor.init(connection, traceLevelConfiguration);
    notificationProcessor.handleProcessorStartEvent(notification);

    verifyNoMoreInteractions(connection);
  }

  @Test
  public void handleProcessorStartEvent_withSkipProcessorSpansByNamespace() {
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
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(configurationComponentLocator);

    List<MuleComponent> skippedProcessors = new ArrayList<>();
    skippedProcessors.add(new MuleComponent("mule", "*"));
    TraceLevelConfiguration traceLevelConfiguration = new TraceLevelConfiguration(true, skippedProcessors);

    notificationProcessor.init(connection, traceLevelConfiguration);
    notificationProcessor.handleProcessorStartEvent(notification);

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
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(configurationComponentLocator);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleProcessorEndEvent(notification);
    verifyNoMoreInteractions(connection);
  }

}