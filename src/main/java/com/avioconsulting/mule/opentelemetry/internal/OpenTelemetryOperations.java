package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.api.traces.ComponentEventContext;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;
import org.mule.runtime.extension.api.runtime.parameter.ParameterResolver;
import org.mule.sdk.api.annotation.deprecated.Deprecated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class OpenTelemetryOperations {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryOperations.class);

  /**
   * Deprecated: Use Get Current Trace Context instead. When OTEL_TRACE_CONTEXT
   * does not pre-exist, there is no way for users to get current transaction id.
   *
   * @param config
   *            {@link OpenTelemetryExtensionConfiguration} Instance
   * @param traceTransactionId
   *            provided by user
   * @param correlationInfo
   *            {@link CorrelationInfo} injected by runtime
   * @return Key-value pair map of context attributes
   */
  @DisplayName("Get Trace Context")
  @Alias("get-trace-context")
  @Deprecated(message = "Use Get Current Trace Context instead. When OTEL_TRACE_CONTEXT does not pre-exist, there is no way for users to get current transaction id.", since = "2.3.0", toRemoveIn = "3.0.0")
  public Map<String, String> getTraceContext(@Config OpenTelemetryExtensionConfiguration config,
      @DisplayName("Trace Transaction Id") @Optional(defaultValue = "#[vars.OTEL_TRACE_CONTEXT.TRACE_TRANSACTION_ID]") ParameterResolver<String> traceTransactionId,
      CorrelationInfo correlationInfo) {
    LOGGER.warn("get-trace-context has been deprecated. Use get-current-trace-context instead");
    return config.getOpenTelemetryConnection().getTraceContext(traceTransactionId.resolve());
  }

  /**
   * Get the trace context for current trace transaction.
   *
   * @param config
   *            {@link OpenTelemetryExtensionConfiguration} Instance
   * @param correlationInfo
   *            {@link CorrelationInfo} (injected by runtime) to extract the
   *            current event id
   * @return Key-value pair map of context attributes
   */
  @DisplayName("Get Current Trace Context")
  @Alias("get-current-trace-context")
  @Summary("Gets the current trace context")
  public Map<String, String> getCurrentTraceContext(
      @Config OpenTelemetryExtensionConfiguration config,
      CorrelationInfo correlationInfo, ComponentLocation location) {
    String eventTransactionId = OpenTelemetryUtil.getEventTransactionId(correlationInfo.getEventId());
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Getting current context for event Id: {}, correlationId: {}, trace transactionId: {} at location {} in container {}",
          correlationInfo.getEventId(), correlationInfo.getCorrelationId(), eventTransactionId,
          location.getLocation(), location.getRootContainerName());
    }
    return config.getOpenTelemetryConnection().getTraceContext(eventTransactionId, ComponentEventContext
        .contextScopedLocationFor(correlationInfo.getEventId(), location.getRootContainerName()));
  }

  /**
   * Deprecated: Use addTransactionTags instead. When OTEL_TRACE_CONTEXT does not
   * pre-exist,there is no way for users to get current transaction id.
   *
   * @param config
   *            {@link OpenTelemetryExtensionConfiguration} provided by the SDK
   * @param tags
   *            {@link Map} of {@link String} Keys and {@link String} Values
   *            containing the tags. Behavior of null values in the map is
   *            undefined and not recommended.
   * @param correlationInfo
   *            {@link CorrelationInfo} from the runtime
   */
  @DisplayName("Add Custom Tags")
  @Deprecated(message = "Use addTransactionTags instead. When OTEL_TRACE_CONTEXT does not pre-exist, there is no way for users to get current transaction id.", since = "2.3.0", toRemoveIn = "3.0.0")
  public void addCustomTags(@Config OpenTelemetryExtensionConfiguration config,
      @DisplayName("Trace Transaction Id") @Optional(defaultValue = "#[vars.OTEL_TRACE_CONTEXT.TRACE_TRANSACTION_ID]") ParameterResolver<String> traceTransactionId,
      Map<String, String> tags,
      CorrelationInfo correlationInfo) {
    LOGGER.warn("add-custom-tags has been deprecated. Use add-transaction-tags instead.");
    config.getOpenTelemetryConnection().getTransactionStore().addTransactionTags(traceTransactionId.resolve(),
        "custom",
        tags);
  }

  /**
   * Add custom tags to an ongoing trace transaction. The tags will be added as
   * attributes to the root span of this transaction.
   * <p>
   * </p>
   * If the transaction's root span previously contained a mapping for the key,
   * the old value is replaced by the new value.
   *
   * @param config
   *            {@link OpenTelemetryExtensionConfiguration} provided by the SDK
   * @param tags
   *            {@link Map} of {@link String} Keys and {@link String} Values
   *            containing the tags. Behavior of null values in the map is
   *            undefined and not recommended.
   * @param correlationInfo
   *            {@link CorrelationInfo} from the runtime
   */
  @DisplayName("Add Transaction Tags")
  public void addTransactionTags(@Config OpenTelemetryExtensionConfiguration config,
      Map<String, String> tags,
      CorrelationInfo correlationInfo) {
    String eventTransactionId = OpenTelemetryUtil.getEventTransactionId(correlationInfo.getEventId());
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Add Transaction Tags for event Id: {}, correlationId: {}, trace transactionId: {}",
          correlationInfo.getEventId(), correlationInfo.getCorrelationId(), eventTransactionId);
    }
    config.getOpenTelemetryConnection().getTransactionStore().addTransactionTags(eventTransactionId,
        "custom",
        tags);
  }

}
