package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

public interface ProcessorComponent {
    boolean canHandle(ComponentIdentifier componentIdentifier);
    TraceComponent getTraceComponent(EnrichedServerNotification notification);
}
