package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import java.util.HashMap;
import java.util.Map;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

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
    tags.put("mule.flow.name", getComponentParameterName(notification));
    tags.put("mule.serverId", notification.getServerId());

    builder.withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(notification.getResourceIdentifier());

    TraceComponent httpSourceTrace = getHttpSourceTrace(notification);
    if (httpSourceTrace != null) {
      tags.putAll(httpSourceTrace.getTags());
      builder.withSpanName(httpSourceTrace.getSpanName())
          .withTransactionId(httpSourceTrace.getTransactionId())
          .withContext(httpSourceTrace.getContext())
          .withTags(tags);
    }
    return builder.build();
  }

  private TraceComponent getHttpSourceTrace(EnrichedServerNotification notification) {
    ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
    if (sourceIdentifier == null)
      return null;
    return ProcessorComponentService.getInstance()
        .getProcessorComponentFor(sourceIdentifier)
        .map(processorComponent -> processorComponent.getStartTraceComponent(notification))
        .orElse(null);
  }
}
