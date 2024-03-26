package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ROUTER;

/**
 * This processor handles any specific operations or sources from Mule Core
 * namespace that are needed for overall tracing.
 * Spans for these processors will be generated irrespective of
 * spanAllProcessors flag on global configuration.
 */
public class MuleCoreProcessorComponent extends AbstractProcessorComponent {

  private List<ComponentType> scopes = Arrays.asList(ROUTER);

  @Override
  protected String getNamespace() {
    return NAMESPACE_MULE;
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("flow-ref");
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return super.canHandle(componentIdentifier)
        || (componentIdentifier instanceof TypedComponentIdentifier
            && scopes.contains(((TypedComponentIdentifier) componentIdentifier).getType()));
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    Map<String, String> tags = new HashMap<>();
    ComponentWrapper componentWrapper = new ComponentWrapper(component,
        configurationComponentLocator);
    if (ComponentsUtil.isFlowRef(component.getLocation())) {
      tags.put("mule.app.processor.flowRef.name", componentWrapper.getParameter("name"));
    }
    return tags;
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    TraceComponent endTraceComponent = super.getEndTraceComponent(notification);
    ComponentWrapper componentWrapper = new ComponentWrapper(notification.getComponent(),
        configurationComponentLocator);
    if (ComponentsUtil.isFlowRef(notification.getComponent().getLocation())) {
      endTraceComponent.getTags().put("mule.app.processor.flowRef.name", componentWrapper.getParameter("name"));
    }
    return endTraceComponent;
  }
}
