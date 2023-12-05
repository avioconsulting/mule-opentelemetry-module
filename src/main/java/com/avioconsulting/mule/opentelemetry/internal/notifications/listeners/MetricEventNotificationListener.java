package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.notification.ExtensionNotification;
import org.mule.runtime.api.notification.ExtensionNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricEventNotificationListener extends AbstractMuleNotificationListener
    implements ExtensionNotificationListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricEventNotificationListener.class);

  public MetricEventNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  public void onNotification(ExtensionNotification notification) {
    LOGGER.trace(
        "===> Received "
            + notification.getClass().getName()
            + ":"
            + notification.getAction().getNamespace()
            + ":"
            + notification.getAction().getIdentifier());
    muleNotificationProcessor.captureCustomMetric(notification);
  }
}
