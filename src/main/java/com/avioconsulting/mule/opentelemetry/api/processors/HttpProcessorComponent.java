package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.Map;

public class HttpProcessorComponent extends GenericProcessorComponent {

    static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/http";
    public static final String NAMESPACE = "http";
    public static final String LISTENER = "listener";
    public static final String REQUESTER = "request";

    @Override
    public boolean canHandle(ComponentIdentifier componentIdentifier) {
        return NAMESPACE.equalsIgnoreCase(componentIdentifier.getNamespace())
                && (LISTENER.equalsIgnoreCase(componentIdentifier.getName())
                    || REQUESTER.equalsIgnoreCase(componentIdentifier.getName()));
    }

    @Override
    public TraceComponent getTraceComponent(EnrichedServerNotification notification) {
        TraceComponent traceComponent = super.getTraceComponent(notification);

        return traceComponent;
    }

}
