package com.avioconsulting.mule.opentelemetry.internal.processor;

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
import java.util.Optional;

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

    TraceComponent.Builder builder = TraceComponent.newBuilder(notification.getResourceIdentifier());

    Map<String, String> tags = new HashMap<>();
    tags.put(MULE_APP_FLOW_NAME.getKey(), notification.getResourceIdentifier());
    tags.put(MULE_SERVER_ID.getKey(), notification.getServerId());
    tags.put(MULE_CORRELATION_ID.getKey(), notification.getEvent().getCorrelationId());

    builder.withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(notification.getResourceIdentifier());

    return builder.build();
  }

  @Override
  public Optional<TraceComponent> getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent startTraceComponent = getStartTraceComponent(notification);
    TraceComponent.Builder builder = startTraceComponent.toBuilder().withSpanKind(SpanKind.SERVER);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
      if (notification.getEvent().getVariables().containsKey(TransactionStore.TRACE_CONTEXT_MAP_KEY)) {
        // When flows are called using flow-ref, the variables may contain the parent
        // span information
        TypedValue<Map<String, String>> contextMap = ((TypedValue<Map<String, String>>) notification.getEvent()
            .getVariables().get(TransactionStore.TRACE_CONTEXT_MAP_KEY));
        Context traceContext = traceContextHandler.getTraceContext(contextMap.getValue(),
            ContextMapGetter.INSTANCE);
        builder.withContext(traceContext);
      }
      return Optional.of(builder.build());
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
    ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, configurationComponentLocator)
        .flatMap(processorComponent -> processorComponent.getSourceStartTraceComponent(notification,
            traceContextHandler))
        .ifPresent(sourceTrace -> {
          SpanKind sourceKind = sourceTrace.getSpanKind() != null ? sourceTrace.getSpanKind()
              : SpanKind.SERVER;
          startTraceComponent.getTags().putAll(sourceTrace.getTags());
          builder.withSpanKind(sourceKind)
              .withSpanName(sourceTrace.getSpanName())
              .withTransactionId(sourceTrace.getTransactionId())
              .withContext(sourceTrace.getContext());
        });
    return Optional.of(builder.build());
  }

  @Override
  public Optional<TraceComponent> getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent traceComponent = getEndTraceComponent(notification);
    TraceComponent.Builder builder = traceComponent.toBuilder().withSpanKind(SpanKind.SERVER);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null) {
      return Optional.of(builder.build());
    }

    // Find if there is a processor component to handle flow source component.
    // If exists, allow it to process notification and build any additional tags to
    // include in a trace.
    ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, configurationComponentLocator)
        .flatMap(processorComponent -> processorComponent.getSourceEndTraceComponent(notification,
            traceContextHandler))
        .ifPresent(sourceTrace -> {
          traceComponent.getTags().putAll(sourceTrace.getTags());
          builder.withStatsCode(sourceTrace.getStatusCode());
          builder.withTags(traceComponent.getTags());
        });
    return Optional.of(builder.build());
  }
}
