package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.providers.NoopOpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigSupplier;
import com.avioconsulting.mule.opentelemetry.internal.OpenTelemetryOperations;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.*;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.ServiceProviderUtil;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.RefName;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.http.api.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Operations(OpenTelemetryOperations.class)
@Configuration
public class OpenTelemetryExtensionConfiguration
    implements Startable, Stoppable, OpenTelemetryConfiguration, OpenTelemetryMetricsConfigSupplier {

  public static final String PROP_MULE_OTEL_METRICS_DISABLED = "mule.otel.metrics.disabled";
  public static final String PROP_MULE_OTEL_TRACING_DISABLED = "mule.otel.tracing.disabled";
  private final Logger logger = LoggerFactory.getLogger(OpenTelemetryExtensionConfiguration.class);
  private static final DataType METRIC_NOTIFICATION_DATA_TYPE = DataType.fromType(MetricBaseNotificationData.class);

  @RefName
  private String configName;

  @Inject
  private HttpService httpService;
  @Inject
  private ExpressionManager expressionManager;
  private AppIdentifier appIdentifier;
  private OpenTelemetryConnection openTelemetryConnection;

  public HttpService getHttpService() {
    return httpService;
  }

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

  @Parameter
  @Optional(defaultValue = "false")
  @Placement(order = 501, tab = "Metrics")
  @Summary("Turn off Metrics for this application.")
  private boolean turnOffMetrics;

  @Parameter
  @Optional
  @Placement(order = 502, tab = "Metrics")
  @DisplayName("Metrics Provider")
  @Summary("OpenTelemetry Metrics Provider")
  private OpenTelemetryMetricsConfigProvider metricsConfigProvider;

  @Override
  public boolean isTurnOffTracing() {
    return System.getProperties().containsKey(PROP_MULE_OTEL_TRACING_DISABLED) ? Boolean
        .parseBoolean(System.getProperty(PROP_MULE_OTEL_TRACING_DISABLED)) : turnOffTracing;
  }

  @Override
  public boolean isTurnOffMetrics() {
    boolean disabled = System.getProperties().containsKey(PROP_MULE_OTEL_METRICS_DISABLED) ? Boolean
        .parseBoolean(System.getProperty(PROP_MULE_OTEL_METRICS_DISABLED)) : turnOffMetrics;
    if (!disabled) {
      disabled = this.metricsConfigProvider == null
          || this.metricsConfigProvider instanceof NoopOpenTelemetryMetricsConfigProvider;
    }
    return disabled;
  }

  // Visible for testing purpose
  OpenTelemetryExtensionConfiguration setTurnOffTracing(boolean turnOffTracing) {
    this.turnOffTracing = turnOffTracing;
    return this;
  }

  @Override
  public OpenTelemetryResource getResource() {
    return resource;
  }

  public OpenTelemetryExtensionConfiguration setResource(OpenTelemetryResource resource) {
    this.resource = resource;
    return this;
  }

  @Override
  public ExporterConfiguration getExporterConfiguration() {
    return exporterConfiguration;
  }

  public OpenTelemetryExtensionConfiguration setExporterConfiguration(ExporterConfiguration exporterConfiguration) {
    this.exporterConfiguration = exporterConfiguration;
    return this;
  }

  @Override
  public TraceLevelConfiguration getTraceLevelConfiguration() {
    return traceLevelConfiguration;
  }

  public OpenTelemetryExtensionConfiguration setTraceLevelConfiguration(
      TraceLevelConfiguration traceLevelConfiguration) {
    this.traceLevelConfiguration = traceLevelConfiguration;
    return this;
  }

  @Override
  public SpanProcessorConfiguration getSpanProcessorConfiguration() {
    return spanProcessorConfiguration;
  }

  public OpenTelemetryExtensionConfiguration setSpanProcessorConfiguration(
      SpanProcessorConfiguration spanProcessorConfiguration) {
    this.spanProcessorConfiguration = spanProcessorConfiguration;
    return this;
  }

  public ExpressionManager getExpressionManager() {
    return expressionManager;
  }

  public OpenTelemetryConnection getOpenTelemetryConnection() {
    return openTelemetryConnection;
  }

  @Override
  public String getConfigName() {
    return configName;
  }

  @Inject
  NotificationListenerRegistry notificationListenerRegistry;

  @Inject
  MuleNotificationProcessor muleNotificationProcessor;

  @Override
  public void start() throws MuleException {
    logger.info("Initiating otel config - '{}'", getConfigName());
    appIdentifier = AppIdentifier.fromEnvironment(expressionManager);
    openTelemetryConnection = OpenTelemetryConnection
        .getInstance(new OpenTelemetryConfigWrapper(this));
    openTelemetryConnection
        .setComponentRegistryService(muleNotificationProcessor.getComponentRegistryService());
    getTraceLevelConfiguration().initMuleComponentsMap();
    muleNotificationProcessor.init(openTelemetryConnection,
        getTraceLevelConfiguration());

    if (isTurnOffTracing()) {
      logger.info("Tracing has been turned off. No listener will be registered.");
    } else {
      notificationListenerRegistry.registerListener(
          new MuleMessageProcessorNotificationListener(muleNotificationProcessor));
      notificationListenerRegistry.registerListener(
          new MulePipelineMessageNotificationListener(muleNotificationProcessor));
      notificationListenerRegistry
          .registerListener(new AsyncMessageNotificationListener(muleNotificationProcessor));
      prepareBatchListener();
    }

    if (isTurnOffMetrics()) {
      logger.info("Metrics has been turned off. No listener will be registered.");
    } else {
      notificationListenerRegistry.registerListener(
          new MetricEventNotificationListener(muleNotificationProcessor),
          extensionNotification -> METRIC_NOTIFICATION_DATA_TYPE
              .isCompatibleWith(extensionNotification.getData().getDataType()));
    }
  }

  private void prepareBatchListener() {
    List<com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotificationListener> listeners = new ArrayList<>();
    ServiceProviderUtil.load(
        com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotificationListener.class
            .getClassLoader(),
        com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotificationListener.class,
        listeners);
    if (listeners.isEmpty()) {
      logger.warn(
          "No modules were registered for batch instrumentation. Batch jobs (if present) will not be instrumented.");
    } else {
      if (listeners.size() > 1) {
        logger.warn(
            "Multiple listeners registered for Batch Notifications. This indicate misconfiguration and may impact performance.");
      }
      OtelBatchNotificationListener otelBatchNotificationListener = new OtelBatchNotificationListener(
          muleNotificationProcessor);
      listeners.forEach(listener -> {
        logger.info("Batch Notification listener registered: {}", listener.getClass().getSimpleName());
        listener.register(otelBatchNotificationListener::onNotification, notificationListenerRegistry);
        BatchHelperUtil.init(listener.getBatchUtil());
        BatchHelperUtil.enableBatchSupport();
      });
    }
  }

  @Override
  public AppIdentifier getAppIdentifier() {
    return appIdentifier;
  }

  @Override
  public OpenTelemetryMetricsConfigProvider getMetricsConfigProvider() {
    return metricsConfigProvider;
  }

  @Override
  public void stop() throws MuleException {

  }
}
