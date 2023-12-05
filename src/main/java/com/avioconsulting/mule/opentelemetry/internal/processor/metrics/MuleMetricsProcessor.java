package com.avioconsulting.mule.opentelemetry.internal.processor.metrics;

import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricEventNotification;
import com.avioconsulting.mule.opentelemetry.internal.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionMeta;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;

public interface MuleMetricsProcessor {
  void captureProcessorMetrics(Component component, Error error, String location,
      SpanMeta spanMeta);

  void captureFlowMetrics(TransactionMeta transactionMeta, String flowName, Exception exception);

  <T> void captureCustomMetric(MetricEventNotification<T> metric);

  MuleMetricsProcessor noop = new MuleMetricsProcessor() {
    @Override
    public void captureProcessorMetrics(Component component, Error error, String location, SpanMeta spanMeta) {
      // Do Nothing;
    }

    @Override
    public void captureFlowMetrics(TransactionMeta transactionMeta, String flowName, Exception exception) {
      // Do Nothing
    }

    @Override
    public <T> void captureCustomMetric(MetricEventNotification<T> metric) {

    }
  };

}
