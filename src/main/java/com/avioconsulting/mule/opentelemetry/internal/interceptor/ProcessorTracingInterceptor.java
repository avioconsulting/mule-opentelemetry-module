package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
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

/**
 * Interceptor to set tracing context information in flow a variable
 * named {@link TransactionStore#TRACE_CONTEXT_MAP_KEY}.
 * See {@link TransactionStore#getTransactionContext(String, ComponentLocation)}
 * for possible
 * entries in the map.
 */
@ThreadSafe
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptor.class);
  private final MuleNotificationProcessor muleNotificationProcessor;
  private final ConfigurationComponentLocator configurationComponentLocator;
  private OpenTelemetryConnection openTelemetryConnection;

  /**
   * Interceptor.
   *
   * @param muleNotificationProcessor
   *            {@link MuleNotificationProcessor} if configured fully to acquire
   *            connection supplier.
   * @param configurationComponentLocator
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
    if (muleNotificationProcessor.getConnectionSupplier() != null) {
      if (openTelemetryConnection == null) {
        openTelemetryConnection = muleNotificationProcessor.getConnectionSupplier().get();
      }
      ProcessorComponent processorComponent = muleNotificationProcessor
          .getProcessorComponent(location.getComponentIdentifier().getIdentifier());
      if (processorComponent == null) {
        // when spanAllProcessor is false, and it's the first generic processor
        String transactionId = openTelemetryConnection.getTransactionStore().transactionIdFor(event);
        event.addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY,
            openTelemetryConnection.getTraceContext(transactionId));
      } else {
        Optional<TraceComponent> traceComponent = configurationComponentLocator
            .find(Location.builderFromStringRepresentation(
                location.getLocation()).build())
            .map(c -> processorComponent.getStartTraceComponent(c, event.getMessage(),
                event.getCorrelationId()));
        if (!traceComponent.isPresent()) {
          LOGGER.warn("Could not build a trace component for {} at {}",
              location.getComponentIdentifier().getIdentifier(), location.getLocation());
          return;
        }
        traceComponent.ifPresent(tc -> {
          LOGGER.info("Creating Span in the interceptor for {} at {}",
              location.getComponentIdentifier().getIdentifier(), location.getLocation());
          openTelemetryConnection.addProcessorSpan(tc, location.getRootContainerName());
          String transactionId = openTelemetryConnection.getTransactionStore().transactionIdFor(event);
          event.addVariable(TransactionStore.TRACE_CONTEXT_MAP_KEY,
              openTelemetryConnection.getTraceContext(transactionId,
                  location));
        });
      }
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Intercepted with logic '{}'", location);
      }
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
