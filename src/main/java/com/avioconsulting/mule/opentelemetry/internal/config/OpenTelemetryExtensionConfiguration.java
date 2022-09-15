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
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Operations(OpenTelemetryOperations.class)
@ConnectionProviders(OpenTelemetryConnectionProvider.class)
@Configuration
public class OpenTelemetryExtensionConfiguration implements Startable, Stoppable {

  public static final String PROP_MULE_OTEL_TRACING_DISABLED = "mule.otel.tracing.disabled";
  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryExtensionConfiguration.class);

  @Parameter
  @Optional(defaultValue = "false")
  @Summary("Turn off tracing for this application.")
  private boolean turnOffTracing;

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

  public boolean isTurnOffTracing() {
    return System.getProperties().containsKey(PROP_MULE_OTEL_TRACING_DISABLED) ? Boolean
        .parseBoolean(System.getProperty(PROP_MULE_OTEL_TRACING_DISABLED)) : turnOffTracing;
  }

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
    if (isTurnOffTracing()) {
      logger.info("{} is set to true. Tracing will be turned off.", PROP_MULE_OTEL_TRACING_DISABLED);
      // Is there a better way to let runtime trigger the configuration shutdown
      // without stopping the application?
      // Raising an exception here will make runtime invoke the stop method
      // but it will kill the application as well, so can't do that here.
      // For now, let's skip the initialization of tracing related components and
      // processors.
      return;
    }
    muleNotificationProcessor.init(
        () -> OpenTelemetryConnection
            .getInstance(new OpenTelemetryConfigWrapper(getResource(),
                getExporterConfiguration().getExporter(), getSpanProcessorConfiguration())),
        getTraceLevelConfiguration());
    notificationListenerRegistry.registerListener(
        new MuleMessageProcessorNotificationListener(muleNotificationProcessor));
    notificationListenerRegistry.registerListener(
        new MulePipelineMessageNotificationListener(muleNotificationProcessor));
  }

  @Override
  public void stop() throws MuleException {
    if (isTurnOffTracing()) {
      logger.info("{} is set to true. Configuration has been stopped.", PROP_MULE_OTEL_TRACING_DISABLED);
    }
  }
}
