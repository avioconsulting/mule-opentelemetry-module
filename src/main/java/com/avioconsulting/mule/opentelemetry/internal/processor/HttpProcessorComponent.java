package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class HttpProcessorComponent extends AbstractProcessorComponent {

  static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/http";
  public static final String NAMESPACE = "http";

  @Override
  protected String getNamespace() {
    return NAMESPACE;
  }

  @Override
  protected List<String> getOperations() {
    return singletonList("request");
  }

  @Override
  protected List<String> getSources() {
    return singletonList("listener");
  }

  @Override
  protected SpanKind getSpanKind() {
    return SpanKind.CLIENT;
  }

  private boolean isRequester(ComponentIdentifier componentIdentifier) {
    return namespaceSupported(componentIdentifier)
        && operationSupported(componentIdentifier);
  }

  private boolean isHttpListener(ComponentIdentifier componentIdentifier) {
    return namespaceSupported(componentIdentifier)
        && sourceSupported(componentIdentifier);
  }

  private boolean isListenerFlowEvent(EnrichedServerNotification notification) {
    return FLOW.equals(notification.getComponent().getIdentifier().getName())
        && isHttpListener(getSourceIdentifier(notification));
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    TraceComponent endTraceComponent = super.getEndTraceComponent(notification);

    // When an error is thrown by http:request, the response message will be on
    // error object.
    Message responseMessage = notification
        .getEvent()
        .getError()
        .map(Error::getErrorMessage)
        .orElse(notification.getEvent().getMessage());
    TypedValue<HttpResponseAttributes> responseAttributes = responseMessage.getAttributes();

    if (responseAttributes.getValue() == null
        || !(responseAttributes.getValue() instanceof HttpResponseAttributes)) {
      // When HTTP Requester executes successfully (eg. 200), notification event DOES
      // NOT include the response attribute.
      // Instead, it includes the original input event. In that case, the attribute
      // may not be http response attributes.
      return endTraceComponent;
    }
    // If HTTP Requester generates an error (eg. 404), then error message does
    // include the HTTP Response attributes.
    TraceComponent.Builder builder = endTraceComponent.toBuilder();
    HttpResponseAttributes attributes = responseAttributes.getValue();
    Map<String, String> tags = new HashMap<>();
    tags.put(HTTP_STATUS_CODE.getKey(), Integer.toString(attributes.getStatusCode()));
    builder.withStatsCode(getSpanStatus(false, attributes.getStatusCode()));
    tags.put(
        HTTP_RESPONSE_CONTENT_LENGTH.getKey(),
        attributes.getHeaders().get("content-length"));
    if (endTraceComponent.getTags() != null)
      tags.putAll(endTraceComponent.getTags());
    return builder.withTags(tags).build();
  }

  private StatusCode getSpanStatus(boolean isServer, int statusCode) {
    StatusCode result;
    int maxStatus = isServer ? 500 : 400;
    if (statusCode >= 100 && statusCode < maxStatus) {
      result = StatusCode.UNSET;
    } else {
      result = StatusCode.ERROR;
    }
    return result;
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {

    TraceComponent traceComponent = super.getStartTraceComponent(notification);

    Map<String, String> requesterTags = getAttributes(notification.getInfo().getComponent(),
        notification.getEvent().getMessage().getAttributes());
    requesterTags.putAll(traceComponent.getTags());

    return TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTags(requesterTags)
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withSpanName(requesterTags.get(HTTP_ROUTE.getKey()))
        .withTransactionId(traceComponent.getTransactionId())
        .withSpanKind(getSpanKind())
        .build();
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> tags = new HashMap<>();
    if (isRequester(component.getIdentifier())) {
      tags.putAll(getRequesterTags(componentWrapper));
    } else {
      HttpRequestAttributes attr = (HttpRequestAttributes) attributes.getValue();
      tags.putAll(attributesToTags(attr));
    }
    return tags;
  }

  private Map<String, String> getRequesterTags(ComponentWrapper componentWrapper) {
    Map<String, String> tags = new HashMap<>();
    String path = componentWrapper.getParameters().get("path");
    Map<String, String> connectionParameters = componentWrapper.getConfigConnectionParameters();
    if (!connectionParameters.isEmpty()) {
      tags.put(HTTP_SCHEME.getKey(), connectionParameters.getOrDefault("protocol", "").toLowerCase());
      tags.put(HTTP_HOST.getKey(), connectionParameters.getOrDefault("host", "").concat(":")
          .concat(connectionParameters.getOrDefault("port", "")));
      tags.put(NET_PEER_NAME.getKey(), connectionParameters.getOrDefault("host", ""));
      tags.put(NET_PEER_PORT.getKey(), connectionParameters.getOrDefault("port", ""));
    }
    Map<String, String> configParameters = componentWrapper.getConfigParameters();
    if (!configParameters.isEmpty()) {
      if (configParameters.containsKey("basePath")
          && !configParameters.get("basePath").equalsIgnoreCase("/")) {
        path = configParameters.get("basePath").concat(path);
      }
    }
    tags.put(HTTP_ROUTE.getKey(), path);
    tags.put(HTTP_METHOD.getKey(), componentWrapper.getParameters().get("method"));
    return tags;
  }

  @Override
  public Optional<TraceComponent> getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    if (!isListenerFlowEvent(notification)) {
      return Optional.empty();
    }
    TypedValue<HttpRequestAttributes> attributesTypedValue = notification.getEvent().getMessage().getAttributes();
    HttpRequestAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = attributesToTags(attributes);
    TraceComponent traceComponent = TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(attributes.getListenerPath()) // In case of wildcard, it may be to generic. Eg. /api/*
        .withContext(traceContextHandler.getTraceContext(attributes.getHeaders(), ContextMapGetter.INSTANCE))
        .build();
    return Optional.of(traceComponent);
  }

  @Override
  public Optional<TraceComponent> getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    // Notification event does not expose any information about HTTP Response
    // object.
    // APIKit based flows use httpStatus variable to set response status code.
    // We use this variable to extract response status code.
    // Not APIKit flows must set this variable.
    TypedValue<?> httpStatus = notification.getEvent().getVariables().get("httpStatus");
    if (httpStatus != null) {
      String statusCode = TypedValue.unwrap(httpStatus).toString();
      TraceComponent.Builder builder = getTraceComponentBuilderFor(notification);
      builder.withTags(singletonMap(HTTP_STATUS_CODE.getKey(), statusCode));
      builder.withStatsCode(getSpanStatus(true, Integer.parseInt(statusCode)));
      return Optional.of(builder.build());
    }
    return Optional.empty();
  }

  private Map<String, String> attributesToTags(HttpRequestAttributes attributes) {
    Map<String, String> tags = new HashMap<>();
    tags.put(HTTP_HOST.getKey(), attributes.getHeaders().get("host"));
    tags.put(HTTP_USER_AGENT.getKey(), attributes.getHeaders().get("user-agent"));
    tags.put(HTTP_METHOD.getKey(), attributes.getMethod());
    tags.put(HTTP_SCHEME.getKey(), attributes.getScheme());
    tags.put(HTTP_ROUTE.getKey(), attributes.getListenerPath());
    tags.put(HTTP_TARGET.getKey(), attributes.getRequestPath());
    tags.put(
        HTTP_FLAVOR.getKey(),
        attributes.getVersion().substring(attributes.getVersion().lastIndexOf("/") + 1));
    // TODO: Support additional request headers to be added in
    // http.request.header.<key>=<header-value> attribute.
    return tags;
  }

}
