package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.mulesoft.extension.mq.api.attributes.AnypointMQMessageAttributes;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;

public class AnypointMQProcessorComponent extends AbstractProcessorComponent {
  @Override
  protected String getNamespace() {
    return "anypoint-mq";
  }

  @Override
  protected List<String> getOperations() {
    return Arrays.asList("publish", "consume", "ack", "nack");
  }

  @Override
  protected List<String> getSources() {
    return Collections.singletonList("subscriber");
  }

  @Override
  protected SpanKind getSpanKind() {
    return SpanKind.CLIENT;
  }

  @Override
  protected String getDefaultSpanName(Map<String, String> tags) {
    return tags.getOrDefault(ANYPOINT_MQ_DESTINATION.getKey(), super.getDefaultSpanName(tags));
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();

    Map<String, String> tags = new HashMap<>();
    tags.put(ANYPOINT_MQ_URL.getKey(), connectionParams.get("url"));
    tags.put(ANYPOINT_MQ_CLIENT_ID.getKey(), connectionParams.get("clientId"));
    addTagIfPresent(componentWrapper.getParameters(), "destination", tags, ANYPOINT_MQ_DESTINATION.getKey());
    if (attributes != null && attributes.getValue() instanceof AnypointMQMessageAttributes) {
      AnypointMQMessageAttributes attrs = (AnypointMQMessageAttributes) attributes.getValue();
      tags.put(ANYPOINT_MQ_MESSAGE_ID.getKey(), attrs.getMessageId());
    }

    return tags;
  }

  @Override
  public Optional<TraceComponent> getSourceTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<AnypointMQMessageAttributes> attributesTypedValue = notification.getEvent().getMessage()
        .getAttributes();
    AnypointMQMessageAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = getAttributes(getSourceComponent(notification).orElse(notification.getComponent()),
        attributesTypedValue);
    TraceComponent traceComponent = TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(attributes.getDestination())
        .withContext(traceContextHandler.getTraceContext(attributes.getProperties(), ContextMapGetter.INSTANCE))
        .build();
    return Optional.of(traceComponent);
  }

}
