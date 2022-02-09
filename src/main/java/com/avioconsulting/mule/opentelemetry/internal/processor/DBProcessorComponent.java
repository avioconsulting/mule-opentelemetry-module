package com.avioconsulting.mule.opentelemetry.internal.processor;

import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

public class DBProcessorComponent extends GenericProcessorComponent {

  public static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/db";
  public static final String NAMESPACE = "db";
  public static final String LISTENER = "listener";
  public static final String OPERATION_SELECT = "select";

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return NAMESPACE.equalsIgnoreCase(componentIdentifier.getNamespace())
        && (LISTENER.equalsIgnoreCase(componentIdentifier.getName())
            || OPERATION_SELECT.equalsIgnoreCase(componentIdentifier.getName()));
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    return super.getStartTraceComponent(notification).toBuilder()
        .withSpanKind(SpanKind.CLIENT)
        .build();
  }
}
