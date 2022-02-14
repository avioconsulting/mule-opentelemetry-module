package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;
import static java.util.Collections.singletonList;

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
      return endTraceComponent;
    }
    HttpResponseAttributes attributes = responseAttributes.getValue();
    Map<String, String> tags = new HashMap<>();
    tags.put(HTTP_STATUS_CODE.getKey(), Integer.toString(attributes.getStatusCode()));
    tags.put(
        HTTP_RESPONSE_CONTENT_LENGTH.getKey(),
        attributes.getHeaders().get("content-length"));
    if (endTraceComponent.getTags() != null)
      tags.putAll(endTraceComponent.getTags());
    return endTraceComponent.toBuilder().withTags(tags).build();
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
  public Optional<TraceComponent> getSourceTraceComponent(EnrichedServerNotification notification,
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
        .withSpanName(attributes.getListenerPath())
        .withContext(traceContextHandler.getTraceContext(attributes, ContextMapGetter.INSTANCE))
        .build();
    return Optional.of(traceComponent);
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

  private enum ContextMapGetter implements TextMapGetter<HttpRequestAttributes> {
    INSTANCE;

    @Override
    public Iterable<String> keys(HttpRequestAttributes httpRequestAttributes) {
      return httpRequestAttributes.getHeaders().keySet();
    }

    @Nullable
    @Override
    public String get(@Nullable HttpRequestAttributes httpRequestAttributes, String s) {
      return httpRequestAttributes == null ? null : httpRequestAttributes.getHeaders().get(s);
    }
  }
}
