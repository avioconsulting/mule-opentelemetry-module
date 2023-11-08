package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.junit.Test;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    when(connection.getTraceContext("random-id", location))
        .thenReturn(traceparentMap);
    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getConnectionSupplier()).thenReturn(() -> connection);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor);

    InterceptionEvent interceptionEvent = mock(InterceptionEvent.class);
    interceptor.before(location, Collections.emptyMap(), interceptionEvent);
    verify(interceptionEvent).addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY, traceparentMap);
  }

  @Test
  public void aroundInterceptProceeds() {
    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor);
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    InterceptionEvent interceptionEvent = mock(InterceptionEvent.class);
    InterceptionAction action = mock(InterceptionAction.class);
    CompletableFuture<InterceptionEvent> aroundResponse = interceptor.around(location, Collections.emptyMap(),
        interceptionEvent, action);
    verify(action).proceed();
  }
}