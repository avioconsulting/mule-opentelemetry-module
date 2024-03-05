package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsProvider;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import io.opentelemetry.api.OpenTelemetry;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;

import java.util.ArrayList;

public class OpenTelemetryMetricsProviderCollection
    extends ArrayList<OpenTelemetryMetricsProvider>
    implements OpenTelemetryMetricsProvider<OpenTelemetryMetricsConfigProvider> {

  @Override
  public void initialize(OpenTelemetryMetricsConfigProvider configProvider, OpenTelemetry openTelemetry) {
    this.forEach(provider -> provider.initialize(configProvider, openTelemetry));
  }

  @Override
  public void stop() {
    this.forEach(OpenTelemetryMetricsProvider::stop);
  }

  @Override
  public void addMeteredComponent(String location) {
    this.forEach(provider -> provider.addMeteredComponent(location));
  }

  @Override
  public void captureProcessorMetrics(Component component, Error error, String location, SpanMeta spanMeta) {
    this.forEach(provider -> provider.captureProcessorMetrics(component, error, location, spanMeta));
  }

  @Override
  public void captureFlowMetrics(TransactionMeta transactionMeta, String flowName, Exception exception) {
    this.forEach(provider -> provider.captureFlowMetrics(transactionMeta, flowName, exception));
  }

  @Override
  public void captureCustomMetric(MetricBaseNotificationData metricNotification) {
    this.forEach(provider -> provider.captureCustomMetric(metricNotification));
  }
}
