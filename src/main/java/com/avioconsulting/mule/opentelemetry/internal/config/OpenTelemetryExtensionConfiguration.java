package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
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
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
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
  @Placement(order = 10)
  @Summary("Open Telemetry Resource Configuration. System or Environment Variables will override this configuration.")
  private OpenTelemetryResource resource;

  /**
   * Open Telemetry Exporter Configuration. System or Environment Variables will
   * override this configuration. See Documentation for variable details.
   */
  @ParameterGroup(name = "Exporter")
  @Placement(order = 20)
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private ExporterConfiguration exporterConfiguration;

  @ParameterGroup(name = "Trace Levels")
  @Placement(order = 30)
  private TraceLevelConfiguration traceLevelConfiguration;

  @ParameterGroup(name = "Span Processor")
  @Placement(order = 40, tab = "Tracer Settings")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private SpanProcessorConfiguration spanProcessorConfiguration;

  public TraceLevelConfiguration getTraceLevelConfiguration() {
    return traceLevelConfiguration;
  }

  public ExporterConfiguration getExporterConfiguration() {
    return exporterConfiguration;
  }

  public SpanProcessorConfiguration getSpanProcessorConfiguration() {
    return spanProcessorConfiguration;
  }

  public OpenTelemetryResource getResource() {
    return resource;
  }

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Inject
  MuleNotificationProcessor muleNotificationProcessor;

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
    muleNotificationProcessor.init(
        () -> OpenTelemetryConnection
            .getInstance(new OpenTelemetryConfigWrapper(getResource(),
                getExporterConfiguration().getExporter(), getSpanProcessorConfiguration())),
        getTraceLevelConfiguration().isSpanAllProcessors());
    notificationListenerRegistry.registerListener(
        new MuleMessageProcessorNotificationListener(muleNotificationProcessor));
    notificationListenerRegistry.registerListener(
        new MulePipelineMessageNotificationListener(muleNotificationProcessor));
  }
}
