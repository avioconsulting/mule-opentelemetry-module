package com.avioconsulting.mule.opentelemetry.api.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.mule.runtime.core.api.el.ExpressionManager;

public interface ProcessorComponent {

  boolean canHandle(ComponentIdentifier componentIdentifier);

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
   * Build a {@link TraceComponent} for start of the {@link Component} processing
   * given {@link Event}
   * 
   * @param component
   *            {@link Component}
   * @param event
   *            {@link Event}
   * @return TraceComponent
   */
  TraceComponent getStartTraceComponent(Component component, Event event);

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
   * @return {@link TraceComponent}
   */
  default TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return null;
  }

  /**
   * For flows with a source component, this method can allow processor components
   * to build source specific traces.
   *
   * @param notification
   *            {@link EnrichedServerNotification}
   * @param traceContextHandler
   *            {@link TraceContextHandler}
   * @return TraceComponent
   */
  default TraceComponent getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return null;
  }

  /**
   * Sets the ExpressionManager for any expression evaluations
   *
   * @param expressionManager
   *            the ExpressionManager to be set
   * @return the updated ProcessorComponent
   */
  ProcessorComponent withExpressionManager(ExpressionManager expressionManager);

  /**
   * Sets the ComponentRegistryService to be used by the ProcessorComponent.
   * This service facilitates management and initialization of component wrappers
   * for
   * various components in a registry.
   *
   * @param componentRegistryService
   *            the ComponentRegistryService instance to be set
   * @return the updated ProcessorComponent instance
   */
  ProcessorComponent withComponentRegistryService(ComponentRegistryService componentRegistryService);
}
