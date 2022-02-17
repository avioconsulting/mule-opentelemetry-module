package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.MULE_APP_FLOW_NAME;
import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.MULE_SERVER_ID;

public class FlowProcessorComponent extends AbstractProcessorComponent {
  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return FLOW.equalsIgnoreCase(componentIdentifier.getName());
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
    tags.put(MULE_APP_FLOW_NAME.getKey(), getComponentParameterName(notification));
    tags.put(MULE_SERVER_ID.getKey(), notification.getServerId());

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
