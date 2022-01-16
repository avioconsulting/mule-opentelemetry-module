package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.junit.Test;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProcessorTracingInterceptorTest {

  @Test
  public void injectContextInVars() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    when(connection.getTraceContext("random-id"))
        .thenReturn(traceparentMap);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor();
    interceptor.setConnectionSupplier(() -> Optional.of(connection));
    ComponentLocation location = mock(ComponentLocation.class);
    InterceptionEvent interceptionEvent = mock(InterceptionEvent.class);
    interceptor.before(location, Collections.emptyMap(), interceptionEvent);
    verify(interceptionEvent).addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY, traceparentMap);
  }
}