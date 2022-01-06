package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.Map;

public class FlowProcessorComponent extends AbstractProcessorComponent {
    @Override
    public boolean canHandle(ComponentIdentifier componentIdentifier) {
        System.out.println(componentIdentifier);
        return FLOW.equalsIgnoreCase(componentIdentifier.getName());
    }

    @Override
    public TraceComponent getTraceComponent(EnrichedServerNotification notification) {
        Map<String, String> tags = new HashMap<>();
        if(!canHandle(notification.getComponent().getIdentifier())){
            throw new RuntimeException("Unsupported component " + notification.getComponent().getIdentifier().toString() + " for flow processor.");
        }
        tags.put("mule.flow.name",getComponentParameterName(notification));
        tags.put("mule.serverId", notification.getServerId());

        return  new TraceComponent(tags, notification.getResourceIdentifier(), notification.getEvent().getCorrelationId());
    }

}
