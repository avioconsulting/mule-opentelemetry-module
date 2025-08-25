package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.util.HttpSpanUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.semconv.HttpAttributes.*;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.*;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class HttpProcessorComponent extends AbstractProcessorComponent {

  static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/http";
  public static final String NAMESPACE = "http";

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpProcessorComponent.class);
  private static final char DOUBLE_QUOTE = '"';

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

    // Sometimes errors such as HTTP:CONNECTIVITY can cause failure before the HTTP
    // is established
    // in such cases error object will be there but not the HTTP Response attributes
    notification.getEvent().getError()
        .ifPresent(error -> endTraceComponent
            .withStatsCode(getSpanStatus(false, 500)));
    if (responseAttributes.getValue() == null
        || !(responseAttributes.getValue() instanceof HttpResponseAttributes)) {
      // When HTTP Requester executes successfully (e.g. 200), notification event DOES
      // NOT include the response attribute.
      // Instead, it includes the original input event. In that case, the attribute
      // may not be http response attributes.
      return endTraceComponent;
    }
    // If HTTP Requester generates an error (e.g. 404), then error message does
    // include the HTTP Response attributes.
    HttpResponseAttributes attributes = responseAttributes.getValue();
    Map<String, String> tags = new HashMap<>();
    tags.put(HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(attributes.getStatusCode()));
    endTraceComponent.withStatsCode(getSpanStatus(false, attributes.getStatusCode()));
    if (attributes.getHeaders().containsKey("content-length")) {
      tags.put(SemanticAttributes.HTTP_RESPONSE_HEADER_CONTENT_LENGTH.getKey(),
          attributes.getHeaders().get("content-length"));
    }
    if (endTraceComponent.getTags() != null)
      tags.putAll(endTraceComponent.getTags());
    return endTraceComponent.withTags(tags);
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
  public TraceComponent getStartTraceComponent(Component component, Event event) {

    TraceComponent traceComponent = super.getStartTraceComponent(component, event);

    Map<String, String> requesterTags = getAttributes(component,
        event.getMessage().getAttributes());
    requesterTags.putAll(traceComponent.getTags());

    return TraceComponent.of(component.getLocation().getRootContainerName(), component.getLocation())
        .withTags(requesterTags)
        .withSpanName(requesterTags.get(HTTP_ROUTE.getKey()))
        .withTransactionId(traceComponent.getTransactionId())
        .withSpanKind(getSpanKind())
        .withEventContextId(traceComponent.getEventContextId());
  }

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = componentWrapperService.getComponentWrapper(component);
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
      tags.put(URL_SCHEME.getKey(), connectionParameters.getOrDefault("protocol", "").toLowerCase());
      tags.put(ServerAttributes.SERVER_ADDRESS.getKey(), connectionParameters.getOrDefault("host", ""));
      tags.put(SERVER_PORT.getKey(), connectionParameters.getOrDefault("port", ""));
    }
    Map<String, String> configParameters = componentWrapper.getConfigParameters();
    if (!configParameters.isEmpty()) {
      if (configParameters.containsKey("basePath")
          && !configParameters.get("basePath").equalsIgnoreCase("/")) {
        path = configParameters.get("basePath").concat(path).intern();
      }
    }
    tags.put(HTTP_ROUTE.getKey(), path);
    tags.put(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), componentWrapper.getParameters().get("method"));
    return tags;
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    if (!isListenerFlowEvent(notification)) {
      return null;
    }
    TypedValue<HttpRequestAttributes> attributesTypedValue = notification.getEvent().getMessage().getAttributes();
    HttpRequestAttributes attributes = attributesTypedValue.getValue();
    Map<String, String> tags = attributesToTags(attributes);
    return TraceComponent.of(notification.getResourceIdentifier(), notification.getComponent().getLocation())
        .withTags(tags)
        .withTransactionId(getTransactionId(notification))
        .withSpanName(HttpSpanUtil.spanName(tags, attributes.getListenerPath()))
        .withContext(traceContextHandler.getTraceContext(attributes.getHeaders(), ContextMapGetter.INSTANCE));
  }

  @Override
  public TraceComponent getSourceEndTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    // Notification event does not expose any information about HTTP Response
    // object.
    // APIKit based flows use httpStatus variable to set response status code.
    // We use this variable to extract response status code.
    // Not APIKit flows must set this variable.
    try {
      TypedValue<?> httpStatus = notification.getEvent().getVariables().get("httpStatus");
      if (httpStatus != null) {
        String statusCode = OpenTelemetryUtil.typedValueToString(httpStatus);
        if (statusCode.charAt(0) == DOUBLE_QUOTE
            && statusCode.charAt(statusCode.length() - 1) == DOUBLE_QUOTE) {
          // When httpStatus is set as JSON string, DW wraps it under additional quotes.
          LOGGER.warn(
              "Received HTTP status code as a String '{}', removing the quotes. It is recommended to set the HTTP status to a number.",
              statusCode);
          statusCode = statusCode.substring(1, statusCode.length() - 1);
        }
        TraceComponent traceComponent = getTraceComponentBuilderFor(notification);
        traceComponent.withTags(singletonMap(HTTP_RESPONSE_STATUS_CODE.getKey(), statusCode));
        traceComponent.withStatsCode(getSpanStatus(true, Integer.parseInt(statusCode)));
        return traceComponent;
      }
    } catch (NumberFormatException nfe) {
      LOGGER.error(
          "Failed to parse httpStatus value to a valid status code - {}", nfe.getLocalizedMessage());
    } catch (Exception ex) {
      LOGGER.warn(
          "Failed to extract httpStatus variable value. Resulted span may not have http status code attribute. - {}",
          ex.getLocalizedMessage());
    }
    return null;
  }

  private Map<String, String> attributesToTags(HttpRequestAttributes attributes) {
    Map<String, String> tags = new HashMap<>();
    // TODO: Server span should have client.address
    // tags.put(SERVER_ADDRESS.getKey(), attributes.getHeaders().get("host"));
    tags.put(UserAgentAttributes.USER_AGENT_ORIGINAL.getKey(), attributes.getHeaders().get("user-agent"));
    tags.put(HTTP_REQUEST_METHOD.getKey(), attributes.getMethod());
    tags.put(URL_SCHEME.getKey(), attributes.getScheme());
    tags.put(HTTP_ROUTE.getKey(), attributes.getListenerPath());
    tags.put(URL_PATH.getKey(), attributes.getRequestPath());
    if (attributes.getQueryString() != null) {
      tags.put(URL_QUERY.getKey(), attributes.getQueryString());
    }
    // TODO: Support additional request headers to be added in
    // http.request.header.<key>=<header-value> attribute.
    return tags;
  }

}
