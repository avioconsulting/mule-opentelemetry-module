package com.avioconsulting.mule.opentelemetry.internal.operations;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;
import com.avioconsulting.mule.opentelemetry.api.config.metrics.MetricAttribute;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricEventNotification;
import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricNotificationAction;
import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricNotificationActionProvider;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.i18n.I18nMessageFactory;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.notification.Fires;
import org.mule.runtime.extension.api.annotation.param.*;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.values.OfValues;
import org.mule.runtime.extension.api.notification.NotificationEmitter;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OpenTelemetryOperations {

  @DisplayName("Get Trace Context")
  @Alias("get-trace-context")
  public Map<String, String> getTraceContext(@Connection Supplier<OpenTelemetryConnection> openTelemetryConnection,
      CorrelationInfo correlationInfo) {
    String transactionId = correlationInfo.getCorrelationId();
    return openTelemetryConnection.get().getTraceContext(transactionId);
  }

  /**
   * Add custom tags to an ongoing trace transaction. The tags will be added as
   * attributes to the root span of this transaction.
   * If the transaction's root span previously contained a mapping for the key,
   * the old value is replaced by the new value.
   *
   * @param openTelemetryConnection
   *            {@link OpenTelemetryConnection} provided by the SDK
   * @param tags
   *            {@link Map} of {@link String} Keys and {@link String} Values
   *            containing the tags. Behavior of null values in the map is
   *            undefined and not recommended.
   * @param correlationInfo
   *            {@link CorrelationInfo} from the runtime
   */
  @DisplayName("Add Custom Tags")
  public void addCustomTags(@Connection Supplier<OpenTelemetryConnection> openTelemetryConnection,
      Map<String, String> tags,
      CorrelationInfo correlationInfo) {
    openTelemetryConnection.get().getTransactionStore().addTransactionTags(correlationInfo.getCorrelationId(),
        "custom",
        tags);
  }

  /**
   * Capture custom metrics for application.
   *
   * @param notificationEmitter
   *            is needed to create metric notifications
   * @param metricName
   *            {@link String} to capture
   * @param value
   *            for the metric record
   * @param metricAttributes
   *            List of {@link MetricAttribute} for captured metric record
   */
  @Fires(MetricNotificationActionProvider.class)
  @DisplayName("Add Custom Metric")
  @Alias("add-custom-metric")
  public void addCustomMetric(NotificationEmitter notificationEmitter,
      @Config OpenTelemetryExtensionConfiguration extensionConfiguration,
      @Placement(order = 1) @OfValues(value = CustomMetricNameValueProvider.class, open = false) String metricName,
      @Placement(order = 2) long value,
      @Placement(order = 3) List<MetricAttribute> metricAttributes) {
    CustomMetricInstrumentDefinition instrumentDefinition = extensionConfiguration
        .getMetricInstrumentDefinitionMap().get(metricName);
    if (instrumentDefinition == null) {
      throw new MuleRuntimeException(I18nMessageFactory.createStaticMessage(
          "Metric instrument details for '%s' not found on the configuration '%s'",
          metricName, extensionConfiguration.getConfigName()));
    }
    if (metricAttributes != null && !metricAttributes.isEmpty()) {
      Set<String> unknownKeys = metricAttributes.stream().map(MetricAttribute::getKey)
          .filter(key -> !instrumentDefinition.getAttributeKeys().contains(key)).collect(Collectors.toSet());
      if (!unknownKeys.isEmpty()) {
        throw new MuleRuntimeException(I18nMessageFactory.createStaticMessage(
            "Attributes '%s' are not configured for metric '%s' on the configuration '%s'",
            unknownKeys.toString(), metricName, extensionConfiguration.getConfigName()));
      }
    }
    MetricEventNotification<Long> longCounter = new MetricEventNotification<Long>()
        .setMetricsInstrumentType(instrumentDefinition.getInstrumentType())
        .setMetricName(metricName)
        .setMetricValue(value).setAttributes(metricAttributes);
    notificationEmitter.fire(MetricNotificationAction.CUSTOM_METRIC, TypedValue.of(longCounter));
  }
}
