package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import org.mule.extensions.jms.api.message.JmsAttributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingOperationValues.PROCESS;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingOperationValues.RECEIVE;

/**
 * Extracts information from the JMS Processor.
 * As of now, information from the listener is not being correctly populated.
 * Mapping of opentelemetry properties to Mule attributes:
 *
 * messaging.consumer.id  = client_id or username, from the connection
 * messaging.system	  = defaults to "jms"
 * messaging.message_id = JMSMessageID from the attributes
 * messaging.destination_kind = either "topic" or "queue", from the attributes
 *
 */
public class JMSProcessorComponent extends AbstractProcessorComponent {
  @Override
  protected String getNamespace() {
    return "jms";
  }

  @Override
  protected List<String> getOperations() {
    return Arrays.asList("publish", "consume", "ack", "publish-consume", "recover-session");
  }

  // to be implemented
  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
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

    tags.put(MESSAGING_CONSUMER_ID.getKey(),
        connectionParams.get("clientId") != null ? connectionParams.get("clientId")
            : connectionParams.get("username"));

    tags.put(MESSAGING_SYSTEM.getKey(),
        connectionParams.get("component:name") != null ? connectionParams.get("component:name")
            : "jms");

    if (attributes != null && attributes.getValue() instanceof JmsAttributes) {
      JmsAttributes attrs = (JmsAttributes) attributes.getValue();
      tags.put(MESSAGING_MESSAGE_ID.getKey(), attrs.getHeaders().getJMSMessageID());
    }

    tags.put(MESSAGING_DESTINATION_KIND.getKey(),
        "topic".equalsIgnoreCase(componentWrapper.getParameters().get("destinationType"))
            ? MessagingDestinationKindValues.TOPIC
            : MessagingDestinationKindValues.QUEUE);

    addTagIfPresent(componentWrapper.getParameters(), "destination", tags, MESSAGING_DESTINATION.getKey());
    // tags.put(MESSAGING_PROTOCOL.getKey(), "http");
    addTagIfPresent(connectionParams, "url", tags, MESSAGING_URL.getKey());
    return tags;
  }

  @Override
  public Optional<TraceComponent> getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<JmsAttributes> attributesTypedValue = notification.getEvent().getMessage()
        .getAttributes();
    JmsAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = getAttributes(getSourceComponent(notification).orElse(notification.getComponent()),
        attributesTypedValue);
    tags.put(MESSAGING_OPERATION.getKey(), PROCESS);
    TraceComponent traceComponent = TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(formattedSpanName(attributes.getHeaders().getJMSDestination().getDestination(), PROCESS))
        .withStatsCode(StatusCode.OK)
        .withSpanKind(SpanKind.CONSUMER)
        .build();

    return Optional.of(traceComponent);
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return getTraceComponentBuilderFor(notification).withStatsCode(StatusCode.OK).build();
  }

  @Override
  public Optional<TraceComponent> getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    return Optional.of(getTraceComponentBuilderFor(notification).withStatsCode(StatusCode.OK).build());
  }
}
