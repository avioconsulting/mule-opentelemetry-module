package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
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
    ProcessorComponent httpProcessorComponent = new HttpProcessorComponent()
        .withConfigurationComponentLocator(componentLocator);
    TraceComponent endTraceComponent = httpProcessorComponent.getStartTraceComponent(notification);

    assertThat(endTraceComponent).isNotNull()
        .extracting("spanName", "location", "spanKind")
        .containsExactly("/test", componentLocation.getLocation(), SpanKind.CLIENT);
    assertThat(endTraceComponent.getTags())
        .hasSize(6)
        .containsEntry("http.method", "GET")
        .containsEntry("mule.app.processor.configRef", "test-config")
        .containsEntry("http.route", "/test")
        .containsEntry("mule.app.processor.docName", "HTTP Request")
        .containsEntry("mule.app.processor.name", "request")
        .containsEntry("mule.app.processor.namespace", "http");
  }

  @Test
  public void onSuccessWithResponseAttributes_getSourceStartTraceComponent() {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    EventContext eventContext = mock(EventContext.class);
    ComponentLocation originatingLocation = getComponentLocation("http", "listener");
    when(eventContext.getOriginatingLocation()).thenReturn(originatingLocation);
    when(event.getContext()).thenReturn(eventContext);

    HttpRequestAttributes requestAttributes = mock(HttpRequestAttributes.class);
    when(requestAttributes.getMethod()).thenReturn("GET");
    when(requestAttributes.getScheme()).thenReturn("HTTP");
    when(requestAttributes.getListenerPath()).thenReturn("/test");
    when(requestAttributes.getRequestPath()).thenReturn("/test");
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
    Optional<TraceComponent> sourceTraceComponent = httpProcessorComponent.getSourceStartTraceComponent(
        notification,
        traceContextHandler);

    assertThat(sourceTraceComponent)
        .isNotEmpty()
        .get()
        .extracting("name", "spanName").containsExactly("test-flow", "/test");
    assertThat(sourceTraceComponent.get().getTags())
        .hasSize(7)
        .containsEntry("http.method", "GET")
        .containsEntry("http.flavor", "1.1")
        .containsEntry("http.route", "/test")
        .containsEntry("http.host", "localhost")
        .containsEntry("http.scheme", "HTTP")
        .containsEntry("http.target", "/test")
        .containsEntry("http.user_agent", "test-unit");
  }

}