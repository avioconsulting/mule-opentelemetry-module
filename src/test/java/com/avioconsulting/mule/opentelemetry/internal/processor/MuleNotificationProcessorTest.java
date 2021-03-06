package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.notification.MessageProcessorNotification;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class MuleNotificationProcessorTest extends AbstractProcessorComponentTest {

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

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor();
    notificationProcessor.init(() -> connection, false);
    notificationProcessor.handleProcessorStartEvent(notification);

    verifyZeroInteractions(connection);
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

    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor();
    notificationProcessor.init(() -> connection, false);
    notificationProcessor.handleProcessorEndEvent(notification);

    verifyZeroInteractions(connection);
  }
}