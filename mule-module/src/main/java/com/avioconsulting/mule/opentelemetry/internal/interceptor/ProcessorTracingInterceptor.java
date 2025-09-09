package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.util.MDCUtil;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.mule.runtime.api.metadata.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.*;
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
  private final ComponentRegistryService componentRegistryService;

  /**
   * Interceptor.
   *
   * @param muleNotificationProcessor
   *            {@link MuleNotificationProcessor} if configured fully to acquire
   *            connection supplier.
   */
  public ProcessorTracingInterceptor(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
    this.componentRegistryService = muleNotificationProcessor.getComponentRegistryService();
  }

  @Override
  public void before(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    try {
      if (!muleNotificationProcessor.getInterceptorProcessorConfig().shouldIntercept(location, event)) {
        return;
      }
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
          addTraceContextMap(event,
              muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(transactionId));
        } else {
          Component component = componentRegistryService.findComponentByLocation(location);

          if (component == null) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("Could not locate a component for {} at {}",
                  location.getComponentIdentifier().getIdentifier(), location.getLocation());
            }
            switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
            return;
          }
          try (TraceComponent traceComponent = processorComponent.getStartTraceComponent(component, event)) {
            if (traceComponent == null) {
              LOGGER.warn("Could not build a trace component for {} at {}",
                  location.getComponentIdentifier().getIdentifier(), location.getLocation());
              switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
              return;
            }
            addBatchTags(traceComponent, event);
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace("Creating Span in the interceptor for {} at {}",
                  location.getComponentIdentifier().getIdentifier(), location.getLocation());
            }
            resolveExpressions(traceComponent,
                muleNotificationProcessor.getOpenTelemetryConnection().getExpressionManager(), event);
            muleNotificationProcessor.getOpenTelemetryConnection().addProcessorSpan(traceComponent,
                getLocationParent(location.getLocation()));
            final String transactionId = getEventTransactionId(event);
            if (isFlowRef(location)) {
              processFlowRef(location, event, traceComponent, transactionId);
            } else {
              addTraceContextMap(event,
                  muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(
                      transactionId,
                      traceComponent.contextScopedLocation()));
            }
          }
        }
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Intercepted with logic '{}' at '{}'",
              location.getComponentIdentifier().getIdentifier().toString(), location.getLocation());
        }
      }
    } catch (Exception ex) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Failed to intercept processor {} at {}, span may not be captured for this processor. Error - {}",
            location.getComponentIdentifier().getIdentifier().toString(), location.getLocation(),
            ex.getLocalizedMessage(), ex);
      }
    }
  }

  private void addTraceContextMap(InterceptionEvent event, Map<String, Object> contextMap) {
    event.addVariable(TRACE_CONTEXT_MAP_KEY,
        contextMap);
    MDCUtil.replaceMDCOtelEntries(contextMap);
  }

  private void processFlowRef(ComponentLocation location, InterceptionEvent event, TraceComponent traceComponent,
      String transactionId) {
    ComponentLocation subFlowLocation = resolveFlowName(
        muleNotificationProcessor.getOpenTelemetryConnection().getExpressionManager(),
        traceComponent, event::asBindingContext, componentRegistryService);
    if (subFlowLocation != null) {
      try (TraceComponent subflowTrace = getSubFlowTraceComponent(subFlowLocation, traceComponent)) {
        muleNotificationProcessor.getOpenTelemetryConnection().addProcessorSpan(subflowTrace,
            location.getLocation());
        addTraceContextMap(event, muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(
            transactionId,
            subflowTrace.contextScopedLocation()));
      }
    } else {
      addTraceContextMap(event, muleNotificationProcessor.getOpenTelemetryConnection().getTraceContext(
          transactionId,
          traceComponent.contextScopedLocation()));
    }
  }

  @Override
  public void after(ComponentLocation location, InterceptionEvent event, Optional<Throwable> thrown) {
    switchTraceContext(event, TRACE_PREV_CONTEXT_MAP_KEY, TRACE_CONTEXT_MAP_KEY);
    if (isFlowRef(location)) {
      switchTraceContext(event, OTEL_FLOW_PREV_CONTEXT_ID, OTEL_FLOW_CONTEXT_ID);
    }
    if (location.getComponentIdentifier().getIdentifier().toString().equals(BATCH_JOB_TAG)) {
      event.addVariable(OTEL_BATCH_PARENT_CONTEXT_ID, getEventTransactionId(event.getContext().getId()));
    }
    if (isBatchStepFirstProcessor(location, event, componentRegistryService)) {
      // This context id will be used for ending a record span
      event.addVariable(OTEL_BATCH_STEP_RECORD_CONTEXT_ID, event.getContext().getId());
      event.removeVariable(OTEL_BATCH_PARENT_CONTEXT_ID);
    }
  }

  private void switchTraceContext(InterceptionEvent event, String removalContextKey, String newContextKey) {
    if (event.getVariables().containsKey(removalContextKey)) {
      TypedValue<?> prevContext = event.getVariables().get(removalContextKey);
      if (removalContextKey.equalsIgnoreCase(TRACE_PREV_CONTEXT_MAP_KEY)) {
        Map<String, Object> contextMap = (Map<String, Object>) prevContext.getValue();
        MDCUtil.replaceMDCOtelEntries(contextMap);
      }
      event.addVariable(newContextKey, prevContext);
      event.removeVariable(removalContextKey);
    }
  }

}
