package com.avioconsulting.mule.opentelemetry.internal.processor;

import java.util.HashMap;
import java.util.Map;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

public class GenericProcessorComponent extends AbstractProcessorComponent {
  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return true;
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    Map<String, String> tags = new HashMap<>(getProcessorCommonTags(notification));
    return TraceComponent.newBuilder(notification.getComponent().getLocation().getLocation())
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withSpanName(notification.getComponent().getIdentifier().getName())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .build();
  }
}
