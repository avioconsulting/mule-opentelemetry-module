package com.avioconsulting.mule.opentelemetry.internal.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.MessageProcessorNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuleMessageProcessorNotificationListener
    implements MessageProcessorNotificationListener<MessageProcessorNotification> {

  private final MuleNotificationProcessor muleNotificationProcessor;
  private Logger logger = LoggerFactory.getLogger(MuleMessageProcessorNotificationListener.class);

  public MuleMessageProcessorNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

  @Override
  public void onNotification(MessageProcessorNotification notification) {
    logger.trace(
        "===> Received "
            + notification.getClass().getName()
            + ":"
            + notification.getActionName());

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
