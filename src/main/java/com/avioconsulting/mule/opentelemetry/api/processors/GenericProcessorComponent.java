package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.Map;

public class GenericProcessorComponent extends AbstractProcessorComponent {
    @Override
    public boolean canHandle(ComponentIdentifier componentIdentifier) {
        return true;
    }

    @Override
    public TraceComponent getTraceComponent(EnrichedServerNotification notification) {
        Map<String, String> tags = new HashMap<>(getProcessorCommonTags(notification));
        return new TraceComponent
                .Builder(notification.getComponent().getIdentifier().getName())
                .tags(tags)
                .transactionId(notification.getEvent().getCorrelationId())
                .spanId(notification.getComponent().getLocation().getLocation())
                .build();
    }
}
