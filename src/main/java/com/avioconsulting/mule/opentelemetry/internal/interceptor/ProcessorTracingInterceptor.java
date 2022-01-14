package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnectionProvider;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.mule.runtime.core.api.lifecycle.LifecycleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    OpenTelemetryConnection.get().ifPresent(connection -> {
      String transactionId = connection.getTransactionStore().transactionIdFor(event);
      event.addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY,
          connection.getTraceContext(transactionId));
    });
  }

}
