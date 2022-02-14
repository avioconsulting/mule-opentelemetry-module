package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

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
    tags.put("mule.flow.name", notification.getResourceIdentifier());
    tags.put("mule.serverId", notification.getServerId());

    builder.withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(notification.getResourceIdentifier());

    return builder.build();
  }

  @Override
  public Optional<TraceComponent> getSourceTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TraceComponent startTraceComponent = getStartTraceComponent(notification);
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null)
      return Optional.of(startTraceComponent);

    TraceComponent.Builder builder = startTraceComponent.toBuilder();
    ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier, configurationComponentLocator)
        .flatMap(processorComponent -> processorComponent.getSourceTraceComponent(notification,
            traceContextHandler))
        .ifPresent(sourceTrace -> {
          startTraceComponent.getTags().putAll(sourceTrace.getTags());
          builder.withSpanName(sourceTrace.getSpanName())
              .withTransactionId(sourceTrace.getTransactionId())
              .withContext(sourceTrace.getContext());
        });
    return Optional.of(builder.build());
  }

}
