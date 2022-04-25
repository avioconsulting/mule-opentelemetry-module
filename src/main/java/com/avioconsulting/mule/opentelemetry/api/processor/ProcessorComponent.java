package com.avioconsulting.mule.opentelemetry.api.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.Optional;

public interface ProcessorComponent {
  boolean canHandle(ComponentIdentifier componentIdentifier);

  ProcessorComponent withConfigurationComponentLocator(ConfigurationComponentLocator configurationComponentLocator);

  /**
   * Build a {@link TraceComponent} for start of a flow-like container or a
   * message processor.
   * 
   * @param notification
   *            {@link EnrichedServerNotification}
   * @return {@link TraceComponent}
   */
  TraceComponent getStartTraceComponent(EnrichedServerNotification notification);

  /**
   * Build a {@link TraceComponent} for end of a flow-like container or a message
   * processor.
   * This may need light processing compared to
   * {@link #getStartTraceComponent(EnrichedServerNotification)}.
   * 
   * @param notification
   *            {@link EnrichedServerNotification}
   * @return {@link TraceComponent}
   */
  TraceComponent getEndTraceComponent(EnrichedServerNotification notification);

  /**
   * If a message processor has a source variation, then this implementation can
   * do more processing of a component.
   * 
   * @param notification
   *            {@link EnrichedServerNotification}
   * @param traceContextHandler
   *            {@link TraceContextHandler} to help extract OpenTelemetry context
   * @return {@link Optional}
   */
  default Optional<TraceComponent> getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return Optional.empty();
  }

  /**
   * For flows with a source component, this method can allow processor components
   * to build source specific traces.
   *
   * @param notification
   * @param traceContextHandler
   * @return Optional trace component
   */
  default Optional<TraceComponent> getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return Optional.empty();
  }

}
