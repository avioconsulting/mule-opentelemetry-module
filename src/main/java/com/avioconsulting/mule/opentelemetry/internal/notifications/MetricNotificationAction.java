package com.avioconsulting.mule.opentelemetry.internal.notifications;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.extension.api.notification.NotificationActionDefinition;

public enum MetricNotificationAction implements NotificationActionDefinition<MetricNotificationAction> {

  CUSTOM_METRIC(DataType.fromType(MetricEventNotification.class));

  private final DataType dataType;

  MetricNotificationAction(DataType dataType) {
    this.dataType = dataType;
  }

  @Override
  public DataType getDataType() {
    return dataType;
  }
}
