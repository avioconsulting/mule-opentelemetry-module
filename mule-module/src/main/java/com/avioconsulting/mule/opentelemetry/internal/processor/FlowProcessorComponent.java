package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.addBatchTags;

public class FlowProcessorComponent extends AbstractProcessorComponent {
  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return namespaceSupported(componentIdentifier)
        && operationSupported(componentIdentifier);
  }

  @Override
  protected String getNamespace() {
    return NAMESPACE_MULE;
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("flow");
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    TraceComponent traceComponent = TraceComponent.of(notification.getResourceIdentifier(),
        notification.getComponent().getLocation());

    Map<String, String> tags = getProcessorCommonTags(notification.getComponent());
    tags.put(MULE_APP_FLOW_NAME.getKey(), notification.getResourceIdentifier());
    tags.put(MULE_SERVER_ID.getKey(), notification.getServerId());
    tags.put(MULE_CORRELATION_ID.getKey(), notification.getEvent().getCorrelationId());

    traceComponent.withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(notification.getResourceIdentifier())
        .withLocation(notification.getResourceIdentifier());
    addBatchTags(traceComponent, notification.getEvent());
    return traceComponent;
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent startTraceComponent = getStartTraceComponent(notification).withSpanKind(SpanKind.SERVER);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
      // Private flows should be treated as internal spans
      startTraceComponent.withSpanKind(SpanKind.INTERNAL);
      if (notification.getEvent().getVariables().containsKey(TransactionStore.TRACE_CONTEXT_MAP_KEY)) {
        // When flows are called using flow-ref, the variables may contain the parent
        // span information
        TypedValue<Map<String, String>> contextMap = ((TypedValue<Map<String, String>>) notification.getEvent()
            .getVariables().get(TransactionStore.TRACE_CONTEXT_MAP_KEY));
        Context traceContext = traceContextHandler.getTraceContext(contextMap.getValue(),
            ContextMapGetter.INSTANCE);
        startTraceComponent.withContext(traceContext);
      }
      return startTraceComponent;
    }
    startTraceComponent.getTags().put(MULE_APP_FLOW_SOURCE_NAME.getKey(), sourceIdentifier.getName());
    startTraceComponent.getTags().put(MULE_APP_FLOW_SOURCE_NAMESPACE.getKey(), sourceIdentifier.getNamespace());
    ComponentWrapper sourceWrapper = componentRegistryService
        .getComponentWrapper(componentRegistryService.findComponentByLocation(
            notification.getEvent().getContext().getOriginatingLocation().getLocation()));
    startTraceComponent.getTags().put(MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey(), sourceWrapper.getConfigRef());
    // Find if there is a processor component to handle flow source component.
    // If exists, allow it to process notification and build any additional tags to
    // include in a trace.
    ProcessorComponent processorComponentFor = ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, expressionManager,
            componentRegistryService);
    if (processorComponentFor != null) {
      TraceComponent sourceTrace = processorComponentFor.getSourceStartTraceComponent(notification,
          traceContextHandler);
      if (sourceTrace != null) {
        SpanKind sourceKind = sourceTrace.getSpanKind() != null ? sourceTrace.getSpanKind()
            : SpanKind.SERVER;
        startTraceComponent.getTags().putAll(sourceTrace.getTags());
        startTraceComponent.withSpanKind(sourceKind)
            .withSpanName(sourceTrace.getSpanName())
            .withTransactionId(sourceTrace.getTransactionId())
            .withContext(sourceTrace.getContext());
      }
    }
    return startTraceComponent;
  }

  @Override
  public TraceComponent getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent traceComponent = getEndTraceComponent(notification);
    if (notification.getException() != null) {
      traceComponent.withStatsCode(StatusCode.ERROR);
    }
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
      return traceComponent;
    }

    // Find if there is a processor component to handle flow source component.
    // If exists, allow it to process notification and build any additional tags to
    // include in a trace.
    ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, expressionManager,
            componentRegistryService);
    if (processorComponent != null) {
      TraceComponent sourceTrace = processorComponent.getSourceEndTraceComponent(notification,
          traceContextHandler);
      if (sourceTrace != null) {
        traceComponent.getTags().putAll(sourceTrace.getTags());
        traceComponent.withStatsCode(sourceTrace.getStatusCode());
      }
    }
    return traceComponent;
  }
}
