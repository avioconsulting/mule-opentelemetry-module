package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.TracerConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.OpenTelemetryOperations;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnectionProvider;
import com.avioconsulting.mule.opentelemetry.internal.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.listeners.MulePipelineMessageNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import javax.inject.Inject;

@Operations(OpenTelemetryOperations.class)
@ConnectionProviders(OpenTelemetryConnectionProvider.class)
@Configuration
public class OpenTelemetryExtensionConfiguration implements Startable {

  /**
   * Open Telemetry Resource Configuration. System or Environment Variables will
   * override this configuration. See Documentation for variable details.
   */
  @ParameterGroup(name = "Resource")
  @Placement(order = 1)
  @Summary("Open Telemetry Resource Configuration. System or Environment Variables will override this configuration.")
  private OpenTelemetryResource resource;

  @ParameterGroup(name = "Tracer")
  @Placement(order = 2)
  private TracerConfiguration tracerConfiguration;

  /**
   * Open Telemetry Exporter Configuration. System or Environment Variables will
   * override this configuration. See Documentation for variable details.
   */
  @Parameter
  @DisplayName(value = "OpenTelemetry Exporter")
  @Optional
  @Summary("Open Telemetry Exporter Configuration. System or Environment Variables will override this configuration.")
  @Placement(order = 3)
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private OpenTelemetryExporter exporter;

  public TracerConfiguration getTracerConfiguration() {
    return tracerConfiguration;
  }

  public OpenTelemetryExporter getExporter() {
    return exporter;
  }

  public OpenTelemetryResource getResource() {
    return resource;
  }

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
        () -> OpenTelemetryConnection
            .getInstance(new OpenTelemetryConfigWrapper(getResource(), getExporter())),
        getTracerConfiguration().isSpanAllProcessors());
    notificationListenerRegistry.registerListener(
        new MuleMessageProcessorNotificationListener(muleNotificationProcessor));
    notificationListenerRegistry.registerListener(
        new MulePipelineMessageNotificationListener(muleNotificationProcessor));
  }
}
