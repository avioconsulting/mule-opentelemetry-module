package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.Notification;
import org.mule.runtime.api.notification.NotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;

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
    replaceMDCEntry(getEvent(notification));
    processNotification(notification);
  }

  protected abstract Event getEvent(T notification);

  /**
   * Process received notification for specific implementation.
   * 
   * @param notification
   */
  protected abstract void processNotification(T notification);

  private void replaceMDCEntry(Event event) {
    if (event == null)
      return;
    replaceMDCEntry(event.getVariables());
  }

  protected void replaceMDCEntry(Map<String, TypedValue<?>> variables) {
    TypedValue<Map<String, Object>> contextMap = (TypedValue<Map<String, Object>>) variables
        .getOrDefault(TransactionStore.TRACE_CONTEXT_MAP_KEY, TypedValue.of(Collections.emptyMap()));
    Map<String, Object> context = contextMap.getValue();
    if (context == null || context.isEmpty())
      return;
    replaceMDCEntry(context, "traceId");
    replaceMDCEntry(context, "traceIdLongLowPart");
    replaceMDCEntry(context, "spanId");
    replaceMDCEntry(context, "spanIdLong");
  }

  private void replaceMDCEntry(Map<String, Object> contextMap, String key) {
    if (contextMap.containsKey(key)) {
      String mdcValue = MDC.get(key);
      if (mdcValue != null && mdcValue.equalsIgnoreCase(contextMap.get(key).toString())) {
        return;
      }
      MDC.remove(key);
      MDC.put(key, contextMap.get(key).toString());
    }
  }
}
