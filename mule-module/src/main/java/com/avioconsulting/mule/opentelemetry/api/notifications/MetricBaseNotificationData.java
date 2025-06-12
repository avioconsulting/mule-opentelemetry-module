package com.avioconsulting.mule.opentelemetry.api.notifications;

import java.util.Map;

/**
 * Metrics notifications data holder to use by Metrics providers for processing
 * custom metrics.
 * 
 * @param <SELF>
 *            class extending this class
 */
public abstract class MetricBaseNotificationData<SELF extends MetricBaseNotificationData<SELF>> {

  private String metricName;
  private Object metricValue;
  private String metricType;
  private Map<String, String> metricAttributes;

  public String getMetricName() {
    return metricName;
  }

  public SELF setMetricName(String metricName) {
    this.metricName = metricName;
    return (SELF) this;
  }

  public Object getMetricValue() {
    return metricValue;
  }

  public SELF setMetricValue(Object metricValue) {
    this.metricValue = metricValue;
    return (SELF) this;
  }

  public String getMetricType() {
    return metricType;
  }

  public SELF setMetricType(String metricType) {
    this.metricType = metricType;
    return (SELF) this;
  }

  public Map<String, String> getMetricAttributes() {
    return metricAttributes;
  }

  public SELF setMetricAttributes(Map<String, String> metricAttributes) {
    this.metricAttributes = metricAttributes;
    return (SELF) this;
  }
}
