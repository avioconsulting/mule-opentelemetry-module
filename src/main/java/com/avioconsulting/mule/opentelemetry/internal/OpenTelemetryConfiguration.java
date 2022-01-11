package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.listeners.MulePipelineMessageNotificationListener;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.extension.api.annotation.Operations;

import javax.inject.Inject;

@Operations(OpenTelemetryOperations.class)
public class OpenTelemetryConfiguration implements Initialisable {

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Override
  public void initialise() throws InitialisationException {
    notificationListenerRegistry.registerListener(new MuleMessageProcessorNotificationListener());
    notificationListenerRegistry.registerListener(new MulePipelineMessageNotificationListener());
  }
}
