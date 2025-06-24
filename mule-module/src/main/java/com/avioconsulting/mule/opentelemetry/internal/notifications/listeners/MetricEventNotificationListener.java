package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.ExtensionNotification;
import org.mule.runtime.api.notification.ExtensionNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metric event notification listener to process {@link ExtensionNotification}s
 * raised by the module.
 */
public class MetricEventNotificationListener extends AbstractMuleNotificationListener<ExtensionNotification>
    implements ExtensionNotificationListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricEventNotificationListener.class);

  public MetricEventNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  protected Event getEvent(ExtensionNotification notification) {
    return notification.getEvent();
  }

  @Override
  protected void processNotification(ExtensionNotification notification) {
    muleNotificationProcessor
        .getOpenTelemetryConnection()
        .getMetricsProviders()
        .captureCustomMetric((MetricBaseNotificationData) notification.getData().getValue());
  }
}
