package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.AsyncMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async work is scheduled and completed beyond the lifecycle of `mule:async`
 * processor execution.
 * This dedicated listener for AsyncMessageNotification will let us process the
 * spans for Aynch chain of processors.
 *
 * @since 2.1.2
 */
public class AsyncMessageNotificationListener extends AbstractMuleNotificationListener<AsyncMessageNotification>
    implements org.mule.runtime.api.notification.AsyncMessageNotificationListener<AsyncMessageNotification> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncMessageNotificationListener.class);

  public AsyncMessageNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  protected Event getEvent(AsyncMessageNotification notification) {
    return notification.getEvent();
  }

  @Override
  protected void processNotification(AsyncMessageNotification notification) {
    switch (Integer.parseInt(notification.getAction().getIdentifier())) {
      case AsyncMessageNotification.PROCESS_ASYNC_SCHEDULED:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Scheduled {}:{} - {}", notification.getEventName(),
              notification.getComponent().getIdentifier().getName(),
              notification.getEvent().getContext().getId());
        }
        muleNotificationProcessor.handleAsyncScheduledEvent(notification);
        break;
      case AsyncMessageNotification.PROCESS_ASYNC_COMPLETE:
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Completed {} - {}", notification.getEventName(),
              notification.getEvent().getContext().getId());
        }
        muleNotificationProcessor.handleProcessorEndEvent(notification);
        break;
    }
  }
}
