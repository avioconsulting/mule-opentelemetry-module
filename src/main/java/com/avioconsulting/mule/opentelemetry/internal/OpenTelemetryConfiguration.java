package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnectionProvider;
import com.avioconsulting.mule.opentelemetry.internal.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.listeners.MulePipelineMessageNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;

import javax.inject.Inject;

@Operations(OpenTelemetryOperations.class)
@ConnectionProviders(OpenTelemetryConnectionProvider.class)
public class OpenTelemetryConfiguration implements Startable {

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Override
  public void start() throws MuleException {
    // This phase is too early to initiate OpenTelemetry SDK. It fails with
    // unresolved Otel dependencies.
    // To defer the SDK initialization, MuleNotificationProcessor accepts a supplier
    // that isn't accessed unless needed.
    // Reaching to an actual notification processor event would mean all
    // dependencies are loaded. That is when supplier
    // fetches the connection.
    // This is unconventional way of Connection handling in custom extensions. There
    // are no operations or sources involved.
    // Adding it here gives an opportunity to use Configuration parameters for
    // initializing the SDK. A future use case.
    // TODO: Find another way to inject connections.
    MuleNotificationProcessor muleNotificationProcessor = new MuleNotificationProcessor(
        OpenTelemetryConnection::getInstance);
    notificationListenerRegistry.registerListener(
        new MuleMessageProcessorNotificationListener(muleNotificationProcessor));
    notificationListenerRegistry.registerListener(
        new MulePipelineMessageNotificationListener(muleNotificationProcessor));
  }
}
