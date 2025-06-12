package com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil;
import org.mule.runtime.api.notification.NotificationListenerRegistry;

import java.util.function.Consumer;

public interface OtelBatchNotificationListener {
  void register(Consumer<OtelBatchNotification> callback, NotificationListenerRegistry registry);

  BatchUtil getBatchUtil();
}
