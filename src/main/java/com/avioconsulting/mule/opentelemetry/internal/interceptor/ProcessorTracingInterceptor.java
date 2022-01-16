package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
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
 * named @{@link TransactionStore#TRACE_CONTEXT_MAP_KEY}.
 * See {@link TransactionStore#getTransactionContext(String)} for possible
 * entries in the map.
 */
@Component
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);
  private Supplier<Optional<OpenTelemetryConnection>> connectionSupplier = () -> OpenTelemetryConnection.get();

  public void setConnectionSupplier(Supplier<Optional<OpenTelemetryConnection>> connectionSupplier) {
    this.connectionSupplier = connectionSupplier;
  }

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    connectionSupplier.get().ifPresent(connection -> {
      String transactionId = connection.getTransactionStore().transactionIdFor(event);
      event.addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY,
          connection.getTraceContext(transactionId));
    });
  }

}
