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
        return new TraceComponent(tags, notification.getComponent().getIdentifier().getName()
                , notification.getEvent().getCorrelationId(), notification.getComponent().getLocation().getLocation());
    }
}
