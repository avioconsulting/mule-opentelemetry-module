package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;

public class WSCProcessorComponent extends AbstractProcessorComponent {
  @Override
  protected String getNamespace() {
    return "wsc";
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("consume");
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  @Override
  protected SpanKind getSpanKind() {
    return SpanKind.CLIENT;
  }

  @Override
  protected <A> void addAttributes(Component component, TypedValue<A> attributes, TraceComponent collector) {
    ComponentWrapper componentWrapper = componentRegistryService.getComponentWrapper(component);
    collector.addTag(WSC_CONSUMER_OPERATION.getKey(), componentWrapper.getParameter("operation"));
    Map<String, String> configConnectionParameters = componentWrapper.getConfigConnectionParameters();
    collector.addTag(WSC_CONFIG_SERVICE.getKey(), configConnectionParameters.get("service"));
    collector.addTag(WSC_CONFIG_PORT.getKey(), configConnectionParameters.get("port"));
    if (configConnectionParameters.containsKey("address")) {
      collector.addTag(WSC_CONFIG_ADDRESS.getKey(), configConnectionParameters.get("address"));
    }
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return super.getEndTraceComponent(notification);
  }
}
