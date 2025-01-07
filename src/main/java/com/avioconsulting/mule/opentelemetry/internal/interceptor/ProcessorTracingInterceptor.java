package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.getEventTransactionId;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.resolveExpressions;

/**
 * Interceptor to set tracing context information in flow a variable
 * named {@link TransactionStore#TRACE_CONTEXT_MAP_KEY}.
 * See {@link TransactionStore#getTransactionContext(String, String)}
 * for possible
 * entries in the map.
 */
@ThreadSafe
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);
  private final MuleNotificationProcessor muleNotificationProcessor;
  private final ConfigurationComponentLocator configurationComponentLocator;

  /**
   * Interceptor.
   *
   * @param muleNotificationProcessor
   *            {@link MuleNotificationProcessor} if configured fully to acquire
   *            connection supplier.
   * @param configurationComponentLocator
   *            to locate mule components
   */
  public ProcessorTracingInterceptor(MuleNotificationProcessor muleNotificationProcessor,
      ConfigurationComponentLocator configurationComponentLocator) {
    this.muleNotificationProcessor = muleNotificationProcessor;
    this.configurationComponentLocator = configurationComponentLocator;
  }

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    // Using an instance of MuleNotificationProcessor here.
    // If the tracing is disabled, the module configuration will not initialize
    // connection supplier.
    if (muleNotificationProcessor.hasConnection()) {
      ProcessorComponent processorComponent = muleNotificationProcessor
          .getProcessorComponent(location.getComponentIdentifier().getIdentifier());
      switchTraceContext(event, TRACE_CONTEXT_MAP_KEY, TRACE_PREV_CONTEXT_MAP_KEY);
      if (isFirstProcessor(location)) {
        switchTraceContext(event, OTEL_FLOW_CONTEXT_ID, OTEL_FLOW_PREV_CONTEXT_ID);
        event.addVariable(OTEL_FLOW_CONTEXT_ID, event.getContext().getId());
      }
      if (processorComponent == null) {
        // when spanAllProcessor is false, and it's the first generic processor
        String transactionId = getEventTransactionId(event);
        event.addVariable(TRACE_CONTEXT_MAP_KEY,
            muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(transactionId));
      } else {
        Component component = configurationComponentLocator
            .find(Location.builderFromStringRepresentation(
                location.getLocation()).build())
            // sub-flows are not beans by definitions,
            // so any processors within sub-flows won't be found by location
            // lookup by identifiers and then match the location to find it
            .orElseGet(() -> configurationComponentLocator
                .find(location.getComponentIdentifier().getIdentifier()).stream()
                .filter(c -> c.getLocation().getLocation().equals(location.getLocation())).findFirst()
                .orElse(null));

        if (component == null) {
          LOGGER.debug("Could not locate a component for {} at {}",
              location.getComponentIdentifier().getIdentifier(), location.getLocation());
          switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
          return;
        }
        TraceComponent traceComponent = processorComponent.getStartTraceComponent(component, event);
        if (traceComponent == null) {
          LOGGER.warn("Could not build a trace component for {} at {}",
              location.getComponentIdentifier().getIdentifier(), location.getLocation());
          switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
          return;
        }
        LOGGER.trace("Creating Span in the interceptor for {} at {}",
            location.getComponentIdentifier().getIdentifier(), location.getLocation());
        resolveExpressions(traceComponent,
            muleNotificationProcessor.getOpenTelemetryConnection().getExpressionManager(), event);
        muleNotificationProcessor.getOpenTelemetryConnection().addProcessorSpan(traceComponent,
            getLocationParent(location.getLocation()));
        final String transactionId = getEventTransactionId(event);
        if (isFlowRef(location)) {
          Optional<ComponentLocation> subFlowLocation = resolveFlowName(
              muleNotificationProcessor.getOpenTelemetryConnection().getExpressionManager(),
              traceComponent, event.asBindingContext(), configurationComponentLocator);
          if (subFlowLocation.isPresent()) {
            TraceComponent subflowTrace = getTraceComponent(subFlowLocation.get(), traceComponent);
            muleNotificationProcessor.getOpenTelemetryConnection().addProcessorSpan(subflowTrace,
                location.getLocation());
            event.addVariable(TRACE_CONTEXT_MAP_KEY,
                muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(transactionId,
                    subflowTrace.contextScopedLocation()));
          } else {
            event.addVariable(TRACE_CONTEXT_MAP_KEY,
                muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(transactionId,
                    traceComponent.contextScopedLocation()));
          }
        } else {
          event.addVariable(TRACE_CONTEXT_MAP_KEY,
              muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(transactionId,
                  traceComponent.contextScopedLocation()));
        }
      }
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Intercepted with logic '{}'", location);
      }
    }
  }

  @Override
  public void after(ComponentLocation location, InterceptionEvent event, Optional<Throwable> thrown) {
    switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
    if (isFlowRef(location))
      switchTraceContext(event, OTEL_FLOW_PREV_CONTEXT_ID, OTEL_FLOW_CONTEXT_ID);
  }

  private void switchTraceContext(InterceptionEvent event, String removalContextKey, String newContextKey) {
    if (event.getVariables().containsKey(removalContextKey)) {
      event.addVariable(newContextKey, event.getVariables().get(removalContextKey));
      event.removeVariable(removalContextKey);
    }
  }

  /**
   *
   * NOTE: Without this #around method, the context variable set in #before do not
   * reflect in final event passed to the
   * intercepted processor.
   *
   *
   * @param location
   *            the location and identification properties of the intercepted
   *            component in the mule app configuration.
   * @param parameters
   *            the parameters of the component as defined in the configuration.
   *            All the values are lazily evaluated so
   *            they will be calculated when
   *            {@link ProcessorParameterValue#resolveValue()} method gets
   *            invoked.
   * @param event
   *            an object that contains the state of the event to be sent to the
   *            component. It may be modified by calling its
   *            mutator methods.
   * @param action
   *            when something other than continuing the interception is desired,
   *            the corresponding method on this object must
   *            be called. The methods on this object return a
   *            {@link CompletableFuture} that may be used to return from this
   *            method.
   * @return a non-null {@code CompletableFuture<InterceptionEvent>}
   */
  @Override
  public CompletableFuture<InterceptionEvent> around(ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters, InterceptionEvent event, InterceptionAction action) {
    if (muleNotificationProcessor.getConnectionSupplier() != null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Variables around the interceptor for {} - {}", location.getLocation(),
            event.getVariables().toString());
      }
    }
    return action.proceed();
  }

}
