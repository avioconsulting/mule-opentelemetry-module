package com.avioconsulting.mule.opentelemetry.api.providers;

import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import io.opentelemetry.api.OpenTelemetry;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;

/**
 * Service interface for Metrics providers
 */
public interface OpenTelemetryMetricsProvider<T extends OpenTelemetryMetricsConfigProvider> {

  /**
   * Initialize provider
   * 
   * @param configProvider
   *            <T>
   */
  void initialize(T configProvider, OpenTelemetry openTelemetry);

  void stop();

  /**
   * Add a single component location for capturing metrics.
   *
   * @param location
   *            {@link String} value of target processor
   */
  void addMeteredComponent(String location);

  /**
   * This method is called for capturing Mule Processor event metrics such as
   * start
   * or end of the execution.
   * Implement this method for capture any processor metrics.
   *
   * @param component
   *            {@link Component} instance of the Processor
   * @param error
   *            Nullable {@link Error} if any associated with the event.
   * @param location
   *            {@link String} of the associated processor
   * @param spanMeta
   *            {@link SpanMeta} for any additional information about related
   *            {@link io.opentelemetry.api.trace.Span}
   */
  void captureProcessorMetrics(Component component, Error error, String location,
      SpanMeta spanMeta);

  /**
   * This method is called when processing start or end of a Mule flow.
   * Implement
   * this method to capture any flow metrics.
   *
   * @param transactionMeta
   *            {@link TransactionMeta} for any information about related
   *            transaction span.
   * @param flowName
   *            {@link String} name of the associated flow
   * @param exception
   *            Nullable {@link Exception} if any associated with the event
   */
  void captureFlowMetrics(TransactionMeta transactionMeta, String flowName,
      Exception exception);

  /**
   * For any notifications raised with {@link MetricBaseNotificationData}'s
   * subclass as a notification data, this module
   * will delegate
   * 
   * @param metricNotification
   *            {@link MetricBaseNotificationData} for the metric
   * @param <N>
   *            Sub-class of {@link MetricBaseNotificationData}
   */
  <N extends MetricBaseNotificationData<N>> void captureCustomMetric(N metricNotification);
}
