package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.test.util.TestInterceptionEvent;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.TRACE_CONTEXT_MAP_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProcessorTracingInterceptorTest extends AbstractInternalTest {

  @Test
  public void injectContextInVars_NonProcessorComponent() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    ComponentIdentifier ci = mock(ComponentIdentifier.class);
    TypedComponentIdentifier tci = mock(TypedComponentIdentifier.class);
    when(tci.getIdentifier()).thenReturn(ci);
    when(location.getComponentIdentifier()).thenReturn(tci);
    when(connection.getTraceContext("random-id"))
        .thenReturn(traceparentMap);
    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getOpenTelemetryConnection()).thenReturn(connection);
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);

    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);

    InterceptionEvent interceptionEvent = mock(InterceptionEvent.class);
    EventContext eventContext = mock(EventContext.class);
    when(eventContext.getId()).thenReturn("random-id");
    when(interceptionEvent.getContext()).thenReturn(eventContext);
    interceptor.before(location, Collections.emptyMap(), interceptionEvent);
    verify(interceptionEvent).addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY, traceparentMap);
  }

  @Test
  public void aroundInterceptProceeds() {
    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    InterceptionEvent interceptionEvent = mock(InterceptionEvent.class);
    InterceptionAction action = mock(InterceptionAction.class);
    CompletableFuture<InterceptionEvent> aroundResponse = interceptor.around(location, Collections.emptyMap(),
        interceptionEvent, action);
    verify(action).proceed();
  }

  @Test
  public void verifyVariableReset() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    ComponentIdentifier ci = mock(ComponentIdentifier.class);
    when(ci.getName()).thenReturn("some");
    TypedComponentIdentifier tci = mock(TypedComponentIdentifier.class);
    when(tci.getIdentifier()).thenReturn(ci);
    when(location.getComponentIdentifier()).thenReturn(tci);
    when(connection.getTraceContext("random-id"))
        .thenReturn(traceparentMap);
    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getOpenTelemetryConnection()).thenReturn(connection);
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);

    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);

    TestInterceptionEvent interceptionEvent = new TestInterceptionEvent("random-id");
    TypedValue<String> preContext = TypedValue.of("prev-context-map");
    interceptionEvent.getVariables().put(TRACE_CONTEXT_MAP_KEY, preContext);

    interceptor.before(location, Collections.emptyMap(), interceptionEvent);

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `before` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, TypedValue.of(traceparentMap))
        .containsEntry(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY, preContext);

    interceptor.after(location, interceptionEvent, Optional.empty());

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `after` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);

  }

  @Test
  public void verifyVariableResetOnComponentNotFound() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    ComponentIdentifier ci = mock(ComponentIdentifier.class);
    when(ci.getName()).thenReturn("some");
    TypedComponentIdentifier tci = mock(TypedComponentIdentifier.class);
    when(tci.getIdentifier()).thenReturn(ci);
    when(location.getComponentIdentifier()).thenReturn(tci);
    when(connection.getTraceContext("random-id"))
        .thenReturn(traceparentMap);

    ProcessorComponent processorComponent = mock(ProcessorComponent.class);

    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getOpenTelemetryConnection()).thenReturn(connection);
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);
    when(muleNotificationProcessor.getProcessorComponent(ci)).thenReturn(processorComponent);

    // Component not found
    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    when(configurationComponentLocator.find(any(Location.class))).thenReturn(Optional.empty());
    when(configurationComponentLocator.find(ci)).thenReturn(Collections.emptyList());

    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);

    TestInterceptionEvent interceptionEvent = new TestInterceptionEvent("random-id");
    TypedValue<String> preContext = TypedValue.of("prev-context-map");
    interceptionEvent.getVariables().put(TRACE_CONTEXT_MAP_KEY, preContext);

    interceptor.before(location, Collections.emptyMap(), interceptionEvent);

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `before` logic execution")
        .describedAs("Should retain previous context as main context")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);

    interceptor.after(location, interceptionEvent, Optional.empty());

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `after` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);

  }

  @Test
  public void verifyVariableResetOnTraceComponentNotFound() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    ComponentIdentifier ci = mock(ComponentIdentifier.class);
    when(ci.getName()).thenReturn("some");
    TypedComponentIdentifier tci = mock(TypedComponentIdentifier.class);
    when(tci.getIdentifier()).thenReturn(ci);
    when(location.getComponentIdentifier()).thenReturn(tci);
    when(connection.getTraceContext("random-id"))
        .thenReturn(traceparentMap);

    ProcessorComponent processorComponent = mock(ProcessorComponent.class);

    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getOpenTelemetryConnection()).thenReturn(connection);
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);
    when(muleNotificationProcessor.getProcessorComponent(ci)).thenReturn(processorComponent);

    Component component = mock(Component.class);
    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    when(configurationComponentLocator.find(any(Location.class))).thenReturn(Optional.of(component));

    // Trace component not found
    when(processorComponent.getStartTraceComponent(any(Component.class), any(Event.class)))
        .thenReturn(null);

    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);

    TestInterceptionEvent interceptionEvent = new TestInterceptionEvent("random-id");
    TypedValue<String> preContext = TypedValue.of("prev-context-map");
    interceptionEvent.getVariables().put(TRACE_CONTEXT_MAP_KEY, preContext);

    interceptor.before(location, Collections.emptyMap(), interceptionEvent);

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `before` logic execution")
        .describedAs("Should retain previous context as main context")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);

    interceptor.after(location, interceptionEvent, Optional.empty());

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `after` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);

  }

  @Test
  public void verifyVariableResetOnTraceComponentFound() {
    OpenTelemetryConnection connection = mock(OpenTelemetryConnection.class);
    TransactionStore transactionStore = mock(TransactionStore.class);
    ExpressionManager expressionManager = mock(ExpressionManager.class);
    when(expressionManager.isExpression(anyString())).thenReturn(false);
    when(connection.getTransactionStore()).thenReturn(transactionStore);
    when(transactionStore.transactionIdFor(any())).thenReturn("random-id");
    when(connection.getExpressionManager()).thenReturn(expressionManager);

    Map<String, String> traceparentMap = Collections.singletonMap("traceparent", "some-value");
    ComponentLocation location = mock(ComponentLocation.class);
    when(location.getLocation()).thenReturn("test-location");
    when(location.getRootContainerName()).thenReturn("test-flow-name");
    ComponentIdentifier ci = mock(ComponentIdentifier.class);
    when(ci.getName()).thenReturn("something");
    TypedComponentIdentifier tci = mock(TypedComponentIdentifier.class);
    when(tci.getIdentifier()).thenReturn(ci);
    when(location.getComponentIdentifier()).thenReturn(tci);

    when(connection.getTraceContext("random-id", "test-event-id/test-location"))
        .thenReturn(traceparentMap);

    ProcessorComponent processorComponent = mock(ProcessorComponent.class);

    // Trace component not found
    TraceComponent traceComponent = TraceComponent.of("test").withLocation("test-location")
        .withEventContextId("test-event-id");
    when(processorComponent.getStartTraceComponent(any(), any())).thenReturn(traceComponent);

    MuleNotificationProcessor muleNotificationProcessor = mock(MuleNotificationProcessor.class);
    when(muleNotificationProcessor.getOpenTelemetryConnection()).thenReturn(connection);
    when(muleNotificationProcessor.hasConnection()).thenReturn(true);
    when(muleNotificationProcessor.getProcessorComponent(any(ComponentIdentifier.class)))
        .thenReturn(processorComponent);

    Component component = mock(Component.class);
    ConfigurationComponentLocator configurationComponentLocator = mock(ConfigurationComponentLocator.class);
    when(configurationComponentLocator.find(any(Location.class))).thenReturn(Optional.of(component));

    ProcessorTracingInterceptor interceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);

    TestInterceptionEvent interceptionEvent = new TestInterceptionEvent("random-id");
    TypedValue<String> preContext = TypedValue.of("prev-context-map");
    interceptionEvent.getVariables().put(TRACE_CONTEXT_MAP_KEY, preContext);

    interceptor.before(location, Collections.emptyMap(), interceptionEvent);

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `before` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, TypedValue.of(traceparentMap))
        .containsEntry(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY, preContext);

    interceptor.after(location, interceptionEvent, Optional.empty());

    assertThat(interceptionEvent.getVariables())
        .as("InterceptionEvent Variables post `after` logic execution")
        .containsEntry(TransactionStore.TRACE_CONTEXT_MAP_KEY, preContext)
        .doesNotContainKey(TransactionStore.TRACE_PREV_CONTEXT_MAP_KEY);
  }

}