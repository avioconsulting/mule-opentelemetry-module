package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentWrapperService;
import com.avioconsulting.mule.opentelemetry.test.util.TestInterceptionEvent;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.Test;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.util.MultiMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpProcessorComponentTest extends AbstractProcessorComponentTest {

  @Test
  public void canHandleHttpRequest() {
    ComponentIdentifier identifier = getMockedIdentifier("http", "request");
    assertThat(new HttpProcessorComponent().canHandle(identifier)).isTrue();
  }

  @Test
  public void canHandleHttpListener() {
    ComponentIdentifier identifier = getMockedIdentifier("http", "listener");
    assertThat(new HttpProcessorComponent().canHandle(identifier)).isTrue();
  }

  @Test
  public void canNotHandleNonHttpNamespace() {
    ComponentIdentifier identifier = getMockedIdentifier("mule", "request");
    assertThat(new HttpProcessorComponent().canHandle(identifier)).isFalse();
  }

  @Test
  public void canNotHandleNonHttpComponents() {
    ComponentIdentifier identifier = getMockedIdentifier("mule", "logger");
    assertThat(new HttpProcessorComponent().canHandle(identifier)).isFalse();
  }

  @Test
  public void onErrorWithNoAttributes_getEndTraceComponent() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    when(event.getContext()).thenReturn(new TestInterceptionEvent.TestEventContext());
    Error error = getError(null);
    when(event.getError()).thenReturn(Optional.of(error));

    ComponentLocation componentLocation = getComponentLocation();
    Component component = getComponent(componentLocation, Collections.emptyMap());

    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);

    HttpProcessorComponent httpProcessorComponent = new HttpProcessorComponent();
    TraceComponent endTraceComponent = httpProcessorComponent.getEndTraceComponent(notification);

    assertThat(endTraceComponent).isNotNull()
        .extracting("errorMessage").isEqualTo("Something failed");
    assertThat(endTraceComponent.getTags()).isEmpty();

  }

  @Test
  public void onErrorWithNonResponseAttributes_getEndTraceComponent() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    when(event.getContext()).thenReturn(new TestInterceptionEvent.TestEventContext());
    Error error = getError(mock(HttpRequestAttributes.class));
    when(event.getError()).thenReturn(Optional.of(error));

    ComponentLocation componentLocation = getComponentLocation();
    Component component = getComponent(componentLocation, Collections.emptyMap());

    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);

    HttpProcessorComponent httpProcessorComponent = new HttpProcessorComponent();
    TraceComponent endTraceComponent = httpProcessorComponent.getEndTraceComponent(notification);

    assertThat(endTraceComponent).isNotNull()
        .extracting("errorMessage").isEqualTo("Something failed");
    assertThat(endTraceComponent.getTags()).isEmpty();
  }

  @Test
  public void onSuccessWithResponseAttributes_getStartTraceComponent() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    when(event.getContext()).thenReturn(new TestInterceptionEvent.TestEventContext());
    HttpResponseAttributes responseAttributes = mock(HttpResponseAttributes.class);
    when(responseAttributes.getStatusCode()).thenReturn(200);
    Map<String, String> headerMap = Collections.singletonMap("content-length", "10");
    when(responseAttributes.getHeaders()).thenReturn(new MultiMap<>(headerMap));
    Message message = getMessage(responseAttributes);
    when(event.getMessage()).thenReturn(message);

    ComponentLocation componentLocation = getComponentLocation();

    Map<String, String> config = new HashMap<>();
    config.put("path", "/test");
    config.put("method", "GET");
    config.put("config-ref", "test-config");
    config.put("doc:name", "HTTP Request");
    Component component = getComponent(componentLocation, config, "http", "request");

    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);

    ConfigurationComponentLocator componentLocator = mock(ConfigurationComponentLocator.class);
    when(componentLocator.find(any(Location.class))).thenReturn(Optional.empty());
    ComponentWrapperService componentWrapperService = mock(ComponentWrapperService.class);
    ComponentWrapper wrapper = new ComponentWrapper(component, componentLocator);
    when(componentWrapperService.getComponentWrapper(component)).thenReturn(wrapper);
    ProcessorComponent httpProcessorComponent = new HttpProcessorComponent()
        .withConfigurationComponentLocator(componentLocator)
        .withComponentWrapperService(componentWrapperService);
    TraceComponent endTraceComponent = httpProcessorComponent.getStartTraceComponent(notification);

    assertThat(endTraceComponent).isNotNull()
        .extracting("spanName", "location", "spanKind")
        .containsExactly("/test", componentLocation.getLocation(), SpanKind.CLIENT);
    assertThat(endTraceComponent.getTags())
        .hasSize(7)
        .containsEntry("http.request.method", "GET")
        .containsEntry("mule.app.processor.configRef", "test-config")
        .containsEntry("http.route", "/test")
        .containsEntry("mule.app.processor.docName", "HTTP Request")
        .containsEntry("mule.app.processor.name", "request")
        .containsEntry("mule.app.processor.namespace", "http")
        .containsEntry("mule.correlationId", "testCorrelationId");
  }

  @Test
  public void onSuccessWithResponseAttributes_getSourceStartTraceComponent() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    ComponentLocation originatingLocation = getComponentLocation("http", "listener");
    TestInterceptionEvent.TestEventContext eventContext = new TestInterceptionEvent.TestEventContext()
        .setOriginatingLocation(originatingLocation);
    when(event.getContext()).thenReturn(eventContext);

    HttpRequestAttributes requestAttributes = mock(HttpRequestAttributes.class);
    when(requestAttributes.getMethod()).thenReturn("GET");
    when(requestAttributes.getScheme()).thenReturn("HTTP");
    when(requestAttributes.getListenerPath()).thenReturn("/test");
    when(requestAttributes.getRequestPath()).thenReturn("/test");
    when(requestAttributes.getQueryString()).thenReturn("a=b&c=d");
    when(requestAttributes.getVersion()).thenReturn("HTTP/1.1");

    Map<String, String> headers = new HashMap<>();
    headers.put("host", "localhost");
    headers.put("user-agent", "test-unit");
    when(requestAttributes.getHeaders()).thenReturn(new MultiMap<>(headers));

    Message message = getMessage(requestAttributes);
    when(event.getMessage()).thenReturn(message);

    ComponentLocation componentLocation = getComponentLocation();

    Map<String, String> config = new HashMap<>();
    config.put("name", "test-flow");
    Component component = getComponent(getComponentLocation(), config, "mule", "flow");

    Exception exception = mock(Exception.class);
    MessageProcessorNotification notification = MessageProcessorNotification.createFrom(event, componentLocation,
        component, exception, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);

    HttpProcessorComponent httpProcessorComponent = new HttpProcessorComponent();
    TraceContextHandler traceContextHandler = mock(TraceContextHandler.class);
    TraceComponent sourceTraceComponent = httpProcessorComponent.getSourceStartTraceComponent(
        notification,
        traceContextHandler);

    assertThat(sourceTraceComponent)
        .isNotNull()
        .extracting("name", "spanName").containsExactly("test-flow", "GET /test");
    assertThat(sourceTraceComponent.getTags())
        .hasSize(6)
        .containsEntry("http.request.method", "GET")
        .containsEntry("http.route", "/test")
        .containsEntry("url.scheme", "HTTP")
        .containsEntry("url.path", "/test")
        .containsEntry("url.query", "a=b&c=d")
        .containsEntry("user_agent.original", "test-unit");
  }

}