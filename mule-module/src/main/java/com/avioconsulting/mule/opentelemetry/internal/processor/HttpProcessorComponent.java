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
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.opentelemetry.semconv.HttpAttributes.*;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.*;
import static java.util.Collections.singletonList;

public class HttpProcessorComponent extends AbstractProcessorComponent {

  static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/http";
  public static final String NAMESPACE = "http";

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpProcessorComponent.class);
  private static final char DOUBLE_QUOTE = '"';

  @Override
  public HttpProcessorComponent withExpressionManager(ExpressionManager expressionManager) {
    // Request and listener identifiers share this handler. Listener lookup can
    // supply null; it must not clear the manager needed by subsequent requests.
    if (expressionManager != null) {
      super.withExpressionManager(expressionManager);
    }
    return this;
  }

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
    endTraceComponent.addTag(HTTP_RESPONSE_STATUS_CODE.getKey(),
        Integer.toString(attributes.getStatusCode()));
    endTraceComponent.withStatsCode(getSpanStatus(false, attributes.getStatusCode()));
    if (attributes.getHeaders().containsKey("content-length")) {
      endTraceComponent.addTag(SemanticAttributes.HTTP_RESPONSE_HEADER_CONTENT_LENGTH.getKey(),
          attributes.getHeaders().get("content-length"));
    }
    return endTraceComponent;
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
    ComponentWrapper componentWrapper = componentRegistryService.getComponentWrapper(component);
    boolean hasUrl = isRequester(component.getIdentifier())
        && componentWrapper.getParameters().containsKey("url");
    if (hasUrl) {
      // The superclass adds attributes already; resolve URLs only here, where the
      // current event is available, and never cache an event's destination.
      getRequesterUrlTags(componentWrapper, event, traceComponent);
    }

    return traceComponent.setName(component.getLocation().getRootContainerName())
        .withLocation(component.getLocation().getLocation())
        .withSpanName(traceComponent.getTag(hasUrl ? HTTP_REQUEST_METHOD.getKey() : HTTP_ROUTE.getKey()));
  }

  @Override
  protected <A> void addAttributes(Component component, TypedValue<A> attributes,
      final TraceComponent collector) {
    ComponentWrapper componentWrapper = componentRegistryService.getComponentWrapper(component);
    if (isRequester(component.getIdentifier())) {
      // A URL overrides all connection/path parameters, even if it cannot be
      // resolved.
      if (!componentWrapper.getParameters().containsKey("url")) {
        getRequesterTags(componentWrapper, collector);
      }
    } else {
      HttpRequestAttributes attr = (HttpRequestAttributes) attributes.getValue();
      attributesToTags(attr, collector);
    }
  }

  private void getRequesterUrlTags(ComponentWrapper componentWrapper, Event event,
      final TraceComponent collector) {
    String method = componentWrapper.getParameters().get("method");
    collector.addTag(HTTP_REQUEST_METHOD.getKey(), method == null || method.isEmpty() ? "GET" : method);
    try {
      String url = componentWrapper.getParameters().get("url");
      if (url == null) {
        return;
      }
      if (expressionManager != null && expressionManager.isExpression(url)) {
        TypedValue<?> resolved = expressionManager.evaluate(url, DataType.STRING, event.asBindingContext());
        if (resolved == null || resolved.getValue() == null) {
          return;
        }
        url = OpenTelemetryUtil.typedValueToString(resolved);
      }
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (scheme == null || host == null || host.isEmpty()
          || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getPort() > 65535) {
        return;
      }
      scheme = scheme.toLowerCase(Locale.ROOT);
      String path = uri.getRawPath();
      if (path == null || path.isEmpty()) {
        path = "/";
      }
      String query = redactQuery(uri.getRawQuery());
      // Java 8 URI retains brackets for IPv6 hosts. Ensure exactly one pair in
      // url.full.
      String authority = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
      if (uri.getPort() != -1) {
        authority += ":" + uri.getPort();
      }
      String sanitizedUrl = scheme + "://" + authority + path + (query == null ? "" : "?" + query);
      collector.addTag(URL_SCHEME.getKey(), scheme);
      collector.addTag(ServerAttributes.SERVER_ADDRESS.getKey(), host);
      collector.addTag(SERVER_PORT.getKey(), Integer.toString(uri.getPort() == -1
          ? ("https".equals(scheme) ? 443 : 80)
          : uri.getPort()));
      collector.addTag(URL_PATH.getKey(), path);
      if (query != null) {
        collector.addTag(URL_QUERY.getKey(), query);
      }
      collector.addTag(URL_FULL.getKey(), sanitizedUrl);
    } catch (Exception ignored) {
      // URLs and expression/parser error messages may contain credentials. Do not
      // log them or fall back to a configured destination that Mule will not use.
    }
  }

  private String redactQuery(String query) {
    if (query == null || query.isEmpty()) {
      return query;
    }
    // Conservatively redact every value, not just known secret parameters. Keep
    // encoded keys and duplicates without decoding/re-encoding URL components.
    StringBuilder sanitized = new StringBuilder();
    for (String parameter : query.split("&", -1)) {
      if (sanitized.length() > 0) {
        sanitized.append('&');
      }
      int separator = parameter.indexOf('=');
      sanitized.append(separator < 0 ? parameter : parameter.substring(0, separator));
      sanitized.append("=REDACTED");
    }
    return sanitized.toString();
  }

  private void getRequesterTags(ComponentWrapper componentWrapper, final TraceComponent collector) {
    String path = componentWrapper.getParameters().get("path");
    Map<String, String> connectionParameters = componentWrapper.getConfigConnectionParameters();
    if (!connectionParameters.isEmpty()) {
      collector.addTag(URL_SCHEME.getKey(), connectionParameters.getOrDefault("protocol", "").toLowerCase());
      collector.addTag(ServerAttributes.SERVER_ADDRESS.getKey(), connectionParameters.getOrDefault("host", ""));
      if (connectionParameters.containsKey("port") && !connectionParameters.get("port").isEmpty()) {
        collector.addTag(SERVER_PORT.getKey(), connectionParameters.get("port"));
      }
    }
    Map<String, String> configParameters = componentWrapper.getConfigParameters();
    if (!configParameters.isEmpty()) {
      if (configParameters.containsKey("basePath")
          && !configParameters.get("basePath").equalsIgnoreCase("/")) {
        path = configParameters.get("basePath").concat(path).intern();
      }
    }
    collector.addTag(HTTP_ROUTE.getKey(), path);
    collector.addTag(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), componentWrapper.getParameters().get("method"));
  }

  @Override
  public TraceComponent getSourceStartTraceComponent(EnrichedServerNotification notification,
      TraceContextHandler traceContextHandler) {
    TypedValue<HttpRequestAttributes> attributesTypedValue = notification.getEvent().getMessage().getAttributes();
    HttpRequestAttributes attributes = attributesTypedValue.getValue();
    TraceComponent traceComponent = traceComponentManager
        .createTraceComponent(getTransactionId(notification), notification.getResourceIdentifier(),
            notification.getComponent().getLocation())
        .withContext(traceContextHandler.getTraceContext(attributes.getHeaders(), ContextMapGetter.INSTANCE));
    attributesToTags(attributes, traceComponent);
    traceComponent.withSpanName(HttpSpanUtil.spanName(traceComponent, attributes.getListenerPath()));
    return traceComponent;
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
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "Received HTTP status code as a String '{}', removing the quotes. It is recommended to set the HTTP status to a number.",
                statusCode);
          }
          statusCode = statusCode.substring(1, statusCode.length() - 1);
        }
        TraceComponent traceComponent = getTraceComponentBuilderFor(notification);
        traceComponent.addTag(HTTP_RESPONSE_STATUS_CODE.getKey(), statusCode);
        traceComponent.withStatsCode(getSpanStatus(true, Integer.parseInt(statusCode)));
        return traceComponent;
      }
    } catch (NumberFormatException nfe) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(
            "Failed to parse httpStatus value to a valid status code - {}", nfe.getLocalizedMessage());
      }
    } catch (Exception ex) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Failed to extract httpStatus variable value. Resulted span may not have http status code attribute. - {}",
            ex.getLocalizedMessage());
      }
    }
    return null;
  }

  private void attributesToTags(HttpRequestAttributes attributes,
      final TraceComponent collector) {
    // TODO: Server span should have client.address
    // tags.put(SERVER_ADDRESS.getKey(), attributes.getHeaders().get("host"));
    collector.addTag(UserAgentAttributes.USER_AGENT_ORIGINAL.getKey(), attributes.getHeaders().get("user-agent"));
    collector.addTag(HTTP_REQUEST_METHOD.getKey(), attributes.getMethod());
    collector.addTag(URL_SCHEME.getKey(), attributes.getScheme());
    collector.addTag(HTTP_ROUTE.getKey(), attributes.getListenerPath());
    collector.addTag(URL_PATH.getKey(), attributes.getRequestPath());
    if (attributes.getQueryString() != null) {
      collector.addTag(URL_QUERY.getKey(), attributes.getQueryString());
    }
    // TODO: Support additional request headers to be added in
    // http.request.header.<key>=<header-value> attribute.
  }

}
