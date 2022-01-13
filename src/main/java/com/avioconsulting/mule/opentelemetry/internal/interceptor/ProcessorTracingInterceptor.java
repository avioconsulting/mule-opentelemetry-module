package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnectionProvider;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.mule.runtime.core.api.lifecycle.LifecycleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Map;

@Component
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);
  private OpenTelemetryConnection openTelemetryConnection;
  private boolean initFailed = false;

  public ProcessorTracingInterceptor() {

  }

  private void init() {
    // We cannot init in the constructor. That will be too early to initiate
    // OpenTelemetry SDK. It fails with unresolved Otel dependencies.
    // TODO: Fina another way of injection for Connection.
    try {
      OpenTelemetryConnectionProvider connectionProvider = new OpenTelemetryConnectionProvider();
      LifecycleUtils.initialiseIfNeeded(connectionProvider);
      openTelemetryConnection = connectionProvider.connect();
    } catch (Exception e) {
      LOGGER.error("Failed to initialize OpenTelemetry Connection provider in interceptor.", e);
      initFailed = true;
    }
  }

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    if (openTelemetryConnection == null && !initFailed)
      init();
    if (initFailed) {
      LOGGER.debug("OpenTelemetry was not initialized successfully. Skipping interception.");
      return;
    }
    String transactionId = openTelemetryConnection.getTransactionStore().transactionIdFor(event);
    Context transactionContext = openTelemetryConnection.getTransactionStore().getTransactionContext(transactionId);
    event.addVariable(TransactionStore.TRACE_TRANSACTION_ID, transactionId);
    try (Scope scope = transactionContext.makeCurrent()) {
      openTelemetryConnection.injectTraceContext(event, FlowVarTextMapSetter.INSTANCE);
    }
  }

  public static enum FlowVarTextMapSetter implements TextMapSetter<InterceptionEvent> {
    INSTANCE;

    @Override
    public void set(@Nullable InterceptionEvent carrier, String key, String value) {
      if (carrier != null)
        carrier.addVariable(key, value);
    }
  }
}
