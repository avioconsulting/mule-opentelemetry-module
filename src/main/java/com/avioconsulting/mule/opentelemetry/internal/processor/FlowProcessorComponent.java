package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.Map;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;

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

    if (!canHandle(notification.getComponent().getIdentifier())) {
      throw new RuntimeException(
          "Unsupported component "
              + notification.getComponent().getIdentifier().toString()
              + " for flow processor.");
    }

    TraceComponent traceComponent = TraceComponent.named(notification.getResourceIdentifier());

    Map<String, String> tags = new HashMap<>();
    tags.put(MULE_APP_FLOW_NAME.getKey(), notification.getResourceIdentifier());
    tags.put(MULE_SERVER_ID.getKey(), notification.getServerId());
    tags.put(MULE_CORRELATION_ID.getKey(), notification.getEvent().getCorrelationId());

    traceComponent.withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(notification.getResourceIdentifier());

    return traceComponent;
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent startTraceComponent = getStartTraceComponent(notification).withSpanKind(SpanKind.SERVER);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
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
    Component sourceComponent = configurationComponentLocator.find(Location.builderFromStringRepresentation(
        notification.getEvent().getContext().getOriginatingLocation().getLocation()).build()).get();
    ComponentWrapper sourceWrapper = new ComponentWrapper(sourceComponent, configurationComponentLocator);
    startTraceComponent.getTags().put(MULE_APP_FLOW_SOURCE_CONFIG_REF.getKey(), sourceWrapper.getConfigRef());
    // Find if there is a processor component to handle flow source component.
    // If exists, allow it to process notification and build any additional tags to
    // include in a trace.
    ProcessorComponent processorComponentFor = ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, configurationComponentLocator);
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
    TraceComponent traceComponent = getEndTraceComponent(notification).withSpanKind(SpanKind.SERVER);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
      return traceComponent;
    }

    // Find if there is a processor component to handle flow source component.
    // If exists, allow it to process notification and build any additional tags to
    // include in a trace.
    ProcessorComponent processorComponent = ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, configurationComponentLocator);
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
