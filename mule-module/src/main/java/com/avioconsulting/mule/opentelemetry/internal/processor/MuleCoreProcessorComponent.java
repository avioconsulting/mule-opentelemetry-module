package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_PROCESSOR_FLOW_REF_NAME;

/**
 * This processor handles any specific operations or sources from Mule Core
 * namespace that are needed for overall tracing.
 * Spans for these processors will be generated irrespective of
 * spanAllProcessors flag on global configuration.
 */
public class MuleCoreProcessorComponent extends AbstractProcessorComponent {

  /**
   * These core containers are to be processed in interceptor
   */
  public static final List<String> CORE_INTERCEPT_SCOPE_ROUTERS = Arrays.asList("flow-ref", "choice",
      "first-successful",
      "until-successful",
      "scatter-gather", "round-robin", "foreach", "parallel-foreach", "try");

  private static List<String> CORE_PROCESSORS;

  @Override
  protected String getNamespace() {
    return NAMESPACE_MULE;
  }

  @Override
  protected List<String> getOperations() {
    return CORE_PROCESSORS;
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  public MuleCoreProcessorComponent() {
    CORE_PROCESSORS = new ArrayList<>(CORE_INTERCEPT_SCOPE_ROUTERS);
    CORE_PROCESSORS.add("async");
  }

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return super.canHandle(componentIdentifier);
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    Map<String, String> tags = new HashMap<>();
    ComponentWrapper componentWrapper = componentWrapperService.getComponentWrapper(component);
    if (ComponentsUtil.isFlowRef(component.getLocation())) {
      tags.put(MULE_APP_PROCESSOR_FLOW_REF_NAME.getKey(),
          componentWrapper.getParameter("name"));
    }
    return tags;
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    TraceComponent endTraceComponent = super.getEndTraceComponent(notification);
    ComponentWrapper componentWrapper = componentWrapperService.getComponentWrapper(notification.getComponent());
    if (ComponentsUtil.isFlowRef(notification.getComponent().getLocation())) {
      endTraceComponent.getTags().put(MULE_APP_PROCESSOR_FLOW_REF_NAME.getKey(),
          componentWrapper.getParameter("name"));
    }
    return endTraceComponent;
  }
}
