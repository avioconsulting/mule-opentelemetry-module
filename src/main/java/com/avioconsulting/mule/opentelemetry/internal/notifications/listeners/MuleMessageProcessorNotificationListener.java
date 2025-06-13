package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.MessageProcessorNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuleMessageProcessorNotificationListener
    extends AbstractMuleNotificationListener<MessageProcessorNotification>
    implements MessageProcessorNotificationListener<MessageProcessorNotification> {
  private final Logger LOGGER = LoggerFactory.getLogger(MuleMessageProcessorNotificationListener.class);

  public MuleMessageProcessorNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  protected Event getEvent(MessageProcessorNotification notification) {
    return notification.getEvent();
  }

  @Override
  protected void processNotification(MessageProcessorNotification notification) {
    switch (Integer.parseInt(notification.getAction().getIdentifier())) {
      case MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE:
        muleNotificationProcessor.handleProcessorStartEvent(notification);
        break;

      case MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE:
        muleNotificationProcessor.handleProcessorEndEvent(notification);
        break;
    }
  }
}
