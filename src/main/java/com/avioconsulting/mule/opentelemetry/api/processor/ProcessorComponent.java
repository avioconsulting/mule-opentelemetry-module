package com.avioconsulting.mule.opentelemetry.api.processor;

import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

public interface ProcessorComponent {
    boolean canHandle(ComponentIdentifier componentIdentifier);
    TraceComponent getStartTraceComponent(EnrichedServerNotification notification);
    TraceComponent getEndTraceComponent(EnrichedServerNotification notification);
}
