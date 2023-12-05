package com.avioconsulting.mule.opentelemetry.internal.notifications;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.MetricAttribute;
import com.avioconsulting.mule.opentelemetry.api.config.metrics.MetricsInstrumentType;

import java.util.List;

public class MetricEventNotification<T> {

  private MetricsInstrumentType metricsInstrumentType;

  public MetricsInstrumentType getMetricsInstrumentType() {
    return metricsInstrumentType;
  }

  public MetricEventNotification<T> setMetricsInstrumentType(MetricsInstrumentType metricsInstrumentType) {
    this.metricsInstrumentType = metricsInstrumentType;
    return this;
  }

  private String metricName;
  private T metricValue;
  private List<MetricAttribute> attributes;

  public String getMetricName() {
    return metricName;
  }

  public MetricEventNotification<T> setMetricName(String metricName) {
    this.metricName = metricName;
    return this;
  }

  public T getMetricValue() {
    return metricValue;
  }

  public MetricEventNotification<T> setMetricValue(T metricValue) {
    this.metricValue = metricValue;
    return this;
  }

  public List<MetricAttribute> getAttributes() {
    return attributes;
  }

  public MetricEventNotification<T> setAttributes(List<MetricAttribute> attributes) {
    this.attributes = attributes;
    return this;
  }
}
