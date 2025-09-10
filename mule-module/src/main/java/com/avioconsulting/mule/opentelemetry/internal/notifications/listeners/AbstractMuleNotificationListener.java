package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.MDCUtil;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.Notification;
import org.mule.runtime.api.notification.NotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMuleNotificationListener<T extends Notification> implements NotificationListener<T> {

  protected final MuleNotificationProcessor muleNotificationProcessor;
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMuleNotificationListener.class);

  public AbstractMuleNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

  @Override
  public void onNotification(T notification) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("===> Received {}:{}", notification.getClass().getName(),
          notification.getAction().getIdentifier());
    }
    if (BatchHelperUtil.shouldSkipThisBatchProcessing(getEvent(notification))) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Batch support is disabled. Batch spans will not be processed for location - {}",
            notification);
      }
      return;
    }
    MDCUtil.replaceMDCOtelEntries(getEvent(notification));
    processNotification(notification);
  }

  protected abstract Event getEvent(T notification);

  /**
   * Process received notification for specific implementation.
   * 
   * @param notification
   */
  protected abstract void processNotification(T notification);

}
