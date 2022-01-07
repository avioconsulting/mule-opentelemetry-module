package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.Map;

public class FlowProcessorComponent extends AbstractProcessorComponent {
    @Override
    public boolean canHandle(ComponentIdentifier componentIdentifier) {
        return FLOW.equalsIgnoreCase(componentIdentifier.getName());
    }

    @Override
    public TraceComponent getTraceComponent(EnrichedServerNotification notification) {

        TraceComponent.Builder builder =
                new TraceComponent.Builder(notification.getResourceIdentifier());

        Map<String, String> tags = new HashMap<>();
        if(!canHandle(notification.getComponent().getIdentifier())){
            throw new RuntimeException("Unsupported component " + notification.getComponent().getIdentifier().toString() + " for flow processor.");
        }
        tags.put("mule.flow.name",getComponentParameterName(notification));
        tags.put("mule.serverId", notification.getServerId());

        builder.tags(tags)
                .transactionId(notification.getEvent().getCorrelationId())
                .spanId(notification.getResourceIdentifier());

        TraceComponent httpSourceTrace = getHttpSourceTrace(notification);
        if(httpSourceTrace != null) {
            tags.putAll(httpSourceTrace.getTags());
            builder.spanId(httpSourceTrace.getSpanId())
                    .transactionId(httpSourceTrace.getTransactionId())
                    .tags(tags);
        }
        return builder.build();
    }

    private TraceComponent getHttpSourceTrace(EnrichedServerNotification notification){
        ComponentIdentifier sourceIdentifier = getSourceIdentifier(notification);
        if(sourceIdentifier == null) return null;
        return ProcessorComponentService
                .getInstance()
                .getProcessorComponentFor(sourceIdentifier)
                .map(processorComponent -> processorComponent.getTraceComponent(notification)).orElse(null);
    }

}
