package com.avioconsulting.mule.opentelemetry.internal.processor.metrics;

import com.avioconsulting.mule.opentelemetry.internal.config.CustomMetricInstrumentHolder;
import com.avioconsulting.mule.opentelemetry.api.config.metrics.MetricsInstrumentType;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricEventNotification;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.internal.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionMeta;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class DefaultMuleMetricsProcessor implements MuleMetricsProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMuleMetricsProcessor.class);
  public static final String UNIT_OF_ONE = "1";
  private final OpenTelemetryConnection openTelemetryConnection;
  /**
   * Capture the number of mule messages triggered by flow invocations
   */
  private LongCounter muleMessageCounter;
  /**
   * Capture the duration of mule message processing
   */
  private DoubleHistogram muleMessageDurationHistogram;
  /**
   * Capture the number of requests processed by processor
   */
  private LongCounter processorRequestCounter;
  /**
   * Capture the duration of processor request execution time
   */
  private DoubleHistogram processorRequestDurationHistogram;

  /**
   * List of mule components to meter
   */
  private final List<String> meteredComponentLocations;

  public DefaultMuleMetricsProcessor(OpenTelemetryConnection openTelemetryConnection,
      List<String> meteredComponentLocations) {
    this.openTelemetryConnection = openTelemetryConnection;
    this.meteredComponentLocations = meteredComponentLocations;
    setupBasicMetrics();
  }

  private void setupBasicMetrics() {
    if (muleMessageCounter == null) {
      muleMessageCounter = openTelemetryConnection.createCounter("mule.app.message.count",
          "Number of Mule Messages triggered from Flow Invocations in the Mule Application", UNIT_OF_ONE);
    }
    if (muleMessageDurationHistogram == null) {
      muleMessageDurationHistogram = openTelemetryConnection.createHistogram("mule.app.message.duration",
          "Duration of Mule Messages");
    }
    if (processorRequestCounter == null) {
      processorRequestCounter = openTelemetryConnection.createCounter("mule.app.processor.request.count",
          "Number of requests processed by a processor during message processing in the Mule Application",
          UNIT_OF_ONE);
    }
    if (processorRequestDurationHistogram == null) {
      processorRequestDurationHistogram = openTelemetryConnection.createHistogram(
          "mule.app.processor.request.duration",
          "Duration of Processor execution");
    }
  }

  public void captureProcessorMetrics(Component component, Error error, String location,
      SpanMeta spanMeta) {
    if (meteredComponentLocations.contains(location)) {
      AttributesBuilder attributesBuilder = Attributes.builder()
          .put(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE,
              component.getIdentifier().getNamespace())
          .put(SemanticAttributes.MULE_APP_PROCESSOR_NAME,
              component.getIdentifier().getName());
      if (error != null) {
        attributesBuilder.put(SemanticAttributes.ERROR_TYPE, error.getErrorType().getIdentifier());
      }
      String value = null;
      if ((value = spanMeta.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey())) != null) {
        attributesBuilder.put(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF, value);
      }
      Attributes attributes = attributesBuilder.build();
      processorRequestCounter.add(1,
          attributes, spanMeta.getContext());
      processorRequestDurationHistogram.record(
          spanMeta.getEndTime().toEpochMilli() - spanMeta.getStartTime().toEpochMilli(), attributes,
          spanMeta.getContext());
    }
  }

  public void captureFlowMetrics(TransactionMeta transactionMeta, String flowName, Exception exception) {
    AttributesBuilder attributesBuilder = Attributes.builder().put(SemanticAttributes.MULE_APP_FLOW_NAME,
        flowName);
    if (exception != null) {
      attributesBuilder.put(SemanticAttributes.ERROR_TYPE, exception.getClass().getName());
    }
    String value = null;
    if ((value = transactionMeta.getTags()
        .get(SemanticAttributes.MULE_APP_FLOW_SOURCE_NAMESPACE.getKey())) != null) {
      attributesBuilder.put(SemanticAttributes.MULE_APP_FLOW_SOURCE_NAMESPACE, value);
    }
    if ((value = transactionMeta.getTags().get(SemanticAttributes.MULE_APP_FLOW_SOURCE_NAME.getKey())) != null) {
      attributesBuilder.put(SemanticAttributes.MULE_APP_FLOW_SOURCE_NAME, value);
    }
    if ((value = transactionMeta.getTags()
        .get(SemanticAttributes.MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey())) != null) {
      attributesBuilder.put(SemanticAttributes.MULE_APP_FLOW_SOURCE_CONFIG_REF, value);
    }
    Attributes attributes = attributesBuilder.build();
    muleMessageCounter.add(1,
        attributes);
    muleMessageDurationHistogram
        .record(transactionMeta.getEndTime().toEpochMilli() - transactionMeta.getStartTime().toEpochMilli(),
            attributes);
  }

  /**
   * Capture metrics data for any custom metricNotification events.
   * 
   * @param metricNotification
   *            {@link MetricEventNotification} containing the metricNotification
   *            data
   * @param <T>
   */
  @Override
  public <T> void captureCustomMetric(MetricEventNotification<T> metricNotification) {
    if (Objects.requireNonNull(metricNotification.getMetricsInstrumentType()) == MetricsInstrumentType.COUNTER) {
      CustomMetricInstrumentHolder<LongCounter> longCounterWrapper = openTelemetryConnection
          .getMetricInstrument(metricNotification.getMetricName());
      if (longCounterWrapper == null) {
        // This should not happen since there is a check in the operation raising this
        // event, but in case.
        LOGGER.warn(
            "{} metricNotification instrument is not configured on global configuration and will not be captured.",
            metricNotification.getMetricName());
        return;
      }
      Attributes attributes = Attributes.empty();
      if (metricNotification.getAttributes() != null && !metricNotification.getAttributes().isEmpty()) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        metricNotification.getAttributes().forEach(attr -> attributesBuilder
            .put(longCounterWrapper.getAttributeKeys().get(attr.getKey()), attr.getValue()));
        attributes = attributesBuilder.build();
      }
      longCounterWrapper.getInstrument().add((Long) metricNotification.getMetricValue(), attributes);
    }
  }
}
