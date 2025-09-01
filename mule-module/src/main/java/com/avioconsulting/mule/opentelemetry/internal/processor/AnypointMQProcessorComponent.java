package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.mulesoft.extension.mq.api.attributes.AnypointMQMessageAttributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_PROCESSOR_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.*;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PROCESS;

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

  private final ConcurrentHashMap<String, String> spanNamesCache = new ConcurrentHashMap<>();

  private String formattedSpanName(final String queueName, final String operation) {
    String key = queueName + operation;
    return spanNamesCache.computeIfAbsent(key, k -> (queueName + " " + operation));
  }

  @Override
  public TraceComponent getStartTraceComponent(Component component, Event event) {
    TraceComponent startTraceComponent = super.getStartTraceComponent(component, event);
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
                  MessagingOperationTypeIncubatingValues.RECEIVE));
    }
    return startTraceComponent;
  }

  @Override
  protected <A> void addAttributes(Component component, TypedValue<A> attributes, Map<String, String> collector) {
    ComponentWrapper componentWrapper = componentRegistryService.getComponentWrapper(component);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();

    collector.put(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), connectionParams.get("clientId"));
    if (attributes != null && attributes.getValue() instanceof AnypointMQMessageAttributes) {
      AnypointMQMessageAttributes attrs = (AnypointMQMessageAttributes) attributes.getValue();
      collector.put(MESSAGING_MESSAGE_ID.getKey(), attrs.getMessageId());
    }
    collector.put(MESSAGING_SYSTEM.getKey(), "anypointmq");

    addTagIfPresent(componentWrapper.getParameters(), "destination", collector,
        MESSAGING_DESTINATION_NAME.getKey());

    addTagIfPresent(connectionParams, "url", collector, UrlAttributes.URL_FULL.getKey());
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<AnypointMQMessageAttributes> attributesTypedValue = notification.getEvent().getMessage()
        .getAttributes();
    AnypointMQMessageAttributes attributes = attributesTypedValue.getValue();
    Component sourceComponent = getSourceComponent(notification);
    if (sourceComponent == null) {
      sourceComponent = notification.getComponent();
    }
    TraceComponent traceComponent = getTraceComponentBuilderFor(notification);
    addAttributes(sourceComponent, attributesTypedValue, traceComponent.getTags());
    traceComponent.getTags().put(MESSAGING_OPERATION_NAME.getKey(), PROCESS);
    return traceComponent
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
