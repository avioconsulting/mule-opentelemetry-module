package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.mulesoft.extension.mq.api.attributes.AnypointMQMessageAttributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;
import static io.opentelemetry.semconv.SemanticAttributes.*;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingOperationValues.PROCESS;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingOperationValues.RECEIVE;

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
    return SpanKind.PRODUCER;
  }

  @Override
  protected String getDefaultSpanName(Map<String, String> tags) {
    if (tags.containsKey(MESSAGING_DESTINATION_NAME.getKey())) {
      return formattedSpanName(tags.get(MESSAGING_DESTINATION_NAME.getKey()), "publish");
    }
    // Retaining for compatibility until SemConv v1.17.0
    if (tags.containsKey(MESSAGING_DESTINATION.getKey())) {
      return formattedSpanName(tags.get(MESSAGING_DESTINATION.getKey()), "send");
    }
    return super.getDefaultSpanName(tags);
  }

  private String formattedSpanName(String queueName, String operation) {
    return String.format("%s %s", queueName, operation);
  }

  @Override
  public TraceComponent getStartTraceComponent(Component component, Message message, String correlationId) {
    TraceComponent startTraceComponent = super.getStartTraceComponent(component, message, correlationId);
    if ("consume".equalsIgnoreCase(startTraceComponent.getTags().get(MULE_APP_PROCESSOR_NAME.getKey()))) {
      // TODO: Handling a different Parent Span than flow containing Consume
      // It may be possible that message was published by a different server flow
      // representing
      // a different trace id than the one created by flow containing Consume
      // operation.
      // Should we add the message span context to Span link?
      startTraceComponent = startTraceComponent.withSpanKind(SpanKind.CONSUMER)
          .withSpanName(
              formattedSpanName(startTraceComponent.getTags().get(MESSAGING_DESTINATION_NAME.getKey()),
                  RECEIVE));
    }
    return startTraceComponent;
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();

    Map<String, String> tags = new HashMap<>();
    // Semantic convention 1.17.0 renamed messaging.consumer_id to
    // messaging.consumer.id
    // SemanticAttributes#MESSAGING_CONSUMER_ID now has new attribute key
    // we are retaining the old attribute for compatibility
    tags.put(MESSAGING_CONSUMER_ID.getKey(), connectionParams.get("clientId"));
    tags.put("messaging.consumer_id", connectionParams.get("clientId"));
    if (attributes != null && attributes.getValue() instanceof AnypointMQMessageAttributes) {
      AnypointMQMessageAttributes attrs = (AnypointMQMessageAttributes) attributes.getValue();
      tags.put(MESSAGING_MESSAGE_ID.getKey(), attrs.getMessageId());
    }
    // Semantic convention 1.17.0 renamed messaging.destination_kind to
    // messaging.destination.kind
    // Semantic convention 1.20.0 marked destination.kind as deprecated,
    // we will maintain it for backward compatibility
    tags.put(MESSAGING_DESTINATION_KIND.getKey(), MessagingDestinationKindValues.QUEUE);
    tags.put("messaging.destination_kind", MessagingDestinationKindValues.QUEUE);
    tags.put(MESSAGING_SYSTEM.getKey(), "anypointmq");

    // Backward compatibility for SemConv v1.17.0
    addTagIfPresent(componentWrapper.getParameters(), "destination", tags, MESSAGING_DESTINATION.getKey());
    tags.put(MESSAGING_PROTOCOL.getKey(), "http");

    addTagIfPresent(componentWrapper.getParameters(), "destination", tags, MESSAGING_DESTINATION_NAME.getKey());

    // MESSAGING_URL is removed in SemConv v1.17.0, retaining for backward
    // compatibility
    addTagIfPresent(connectionParams, "url", tags, MESSAGING_URL.getKey());
    return tags;
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<AnypointMQMessageAttributes> attributesTypedValue = notification.getEvent().getMessage()
        .getAttributes();
    AnypointMQMessageAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = getAttributes(getSourceComponent(notification).orElse(notification.getComponent()),
        attributesTypedValue);
    tags.put(MESSAGING_OPERATION.getKey(), PROCESS);
    return TraceComponent.named(notification.getResourceIdentifier())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(formattedSpanName(attributes.getDestination(), PROCESS))
        .withStatsCode(StatusCode.OK)
        .withSpanKind(SpanKind.CONSUMER)
        .withContext(
            traceContextHandler.getTraceContext(attributes.getProperties(), ContextMapGetter.INSTANCE));
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return getTraceComponentBuilderFor(notification).withStatsCode(StatusCode.OK);
  }

  @Override
  public TraceComponent getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return getTraceComponentBuilderFor(notification).withStatsCode(StatusCode.OK);
  }
}
