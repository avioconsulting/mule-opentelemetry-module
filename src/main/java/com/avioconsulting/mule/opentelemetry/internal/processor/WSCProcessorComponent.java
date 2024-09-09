package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  protected String getDefaultSpanName(Map<String, String> tags) {
    return tags.get("mule.wsc.config.service") + ":" + tags.get("mule.wsc.consumer.operation");
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> tags = new HashMap<>();
    tags.put("mule.wsc.consumer.operation", componentWrapper.getParameter("operation"));
    Map<String, String> configConnectionParameters = componentWrapper.getConfigConnectionParameters();
    tags.put("mule.wsc.config.service", configConnectionParameters.get("service"));
    tags.put("mule.wsc.config.port", configConnectionParameters.get("port"));
    if (configConnectionParameters.containsKey("address")) {
      tags.put("mule.wsc.config.address", configConnectionParameters.get("address"));
    }
    return tags;
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return super.getEndTraceComponent(notification);
  }
}
