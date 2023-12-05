package com.avioconsulting.mule.opentelemetry.internal.notifications;

import org.mule.runtime.extension.api.annotation.notification.NotificationActionProvider;
import org.mule.runtime.extension.api.notification.NotificationActionDefinition;

import java.util.HashSet;
import java.util.Set;

public class MetricNotificationActionProvider implements NotificationActionProvider {
  @Override
  public Set<NotificationActionDefinition> getNotificationActions() {
    Set<NotificationActionDefinition> actions = new HashSet<>();
    actions.add(MetricNotificationAction.CUSTOM_METRIC);
    return actions;
  }
}
