package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;

import java.util.Map;
import java.util.function.Supplier;

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

}
