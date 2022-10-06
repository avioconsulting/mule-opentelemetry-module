package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Interceptor to set tracing context information in flow a variable
 * named {@link TransactionStore#TRACE_CONTEXT_MAP_KEY}.
 * See {@link TransactionStore#getTransactionContext(String)} for possible
 * entries in the map.
 */
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);
  private MuleNotificationProcessor muleNotificationProcessor;

  /**
   * Interceptor.
   * 
   * @param muleNotificationProcessor
   * @{@link MuleNotificationProcessor} if configured fully to acquire
   *         connection supplier.
   */
  public ProcessorTracingInterceptor(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    // Using an instance of MuleNotificationProcessor here.
    // If the tracing is disabled, the module configuration will not initialize
    // connection supplier.
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("May Intercept with logic '{}'", location);
    }
    if (muleNotificationProcessor.getConnectionSupplier() != null
        && !event.getVariables().containsKey(TransactionStore.TRACE_CONTEXT_MAP_KEY)) {
      OpenTelemetryConnection openTelemetryConnection = muleNotificationProcessor.getConnectionSupplier().get();
      String transactionId = openTelemetryConnection.getTransactionStore().transactionIdFor(event);
      event.addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY,
          openTelemetryConnection.getTraceContext(transactionId));
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Intercepted with logic '{}'", location);
      }
    }
  }

}
