package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.mulesoft.extension.mq.api.attributes.AnypointMQMessageAttributes;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingOperationValues.RECEIVE;

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
    if (tags.containsKey(MESSAGING_DESTINATION.getKey())) {
      return formattedSpanName(tags.get(MESSAGING_DESTINATION.getKey()), "send");
    }
    return super.getDefaultSpanName(tags);
  }

  private String formattedSpanName(String queueName, String operation) {
    return String.format("%s %s", queueName, operation);
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    TraceComponent startTraceComponent = super.getStartTraceComponent(notification);
    if ("consume".equalsIgnoreCase(startTraceComponent.getTags().get(MULE_APP_PROCESSOR_NAME.getKey()))) {
      // TODO: Handling a different Parent Span than flow containing Consume
      // It may be possible that message was published by a different server flow
      // representing
      // a different trace id than the one created by flow containing Consume
      // operation.
      // Should we add the message span context to Span link?
      startTraceComponent = startTraceComponent.toBuilder().withSpanKind(SpanKind.CONSUMER)
          .withSpanName(formattedSpanName(startTraceComponent.getTags().get(MESSAGING_DESTINATION.getKey()),
              RECEIVE))
          .build();
    }
    return startTraceComponent;
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();

    Map<String, String> tags = new HashMap<>();
    tags.put(MESSAGING_CONSUMER_ID.getKey(), connectionParams.get("clientId"));
    if (attributes != null && attributes.getValue() instanceof AnypointMQMessageAttributes) {
      AnypointMQMessageAttributes attrs = (AnypointMQMessageAttributes) attributes.getValue();
      tags.put(MESSAGING_MESSAGE_ID.getKey(), attrs.getMessageId());
    }
    tags.put(MESSAGING_DESTINATION_KIND.getKey(), MessagingDestinationKindValues.QUEUE);
    tags.put(MESSAGING_SYSTEM.getKey(), "anypointmq");
    addTagIfPresent(componentWrapper.getParameters(), "destination", tags, MESSAGING_DESTINATION.getKey());
    tags.put(MESSAGING_PROTOCOL.getKey(), "http");
    addTagIfPresent(connectionParams, "url", tags, MESSAGING_URL.getKey());
    return tags;
  }

  @Override
  public Optional<TraceComponent> getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<AnypointMQMessageAttributes> attributesTypedValue = notification.getEvent().getMessage()
        .getAttributes();
    AnypointMQMessageAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = getAttributes(getSourceComponent(notification).orElse(notification.getComponent()),
        attributesTypedValue);
    tags.put(MESSAGING_OPERATION.getKey(), RECEIVE);
    TraceComponent traceComponent = TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(formattedSpanName(attributes.getDestination(), RECEIVE))
        .withSpanKind(SpanKind.CONSUMER)
        .withContext(traceContextHandler.getTraceContext(attributes.getProperties(), ContextMapGetter.INSTANCE))
        .build();
    return Optional.of(traceComponent);
  }

}
