package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.connection.TraceContextHandler;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.util.MultiMap;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class HttpProcessorComponentSourceTest extends AbstractProcessorComponentTest {

  @Parameterized.Parameter(value = 0)
  public String httpStatus;

  @Parameterized.Parameter(value = 1)
  public StatusCode spanStatusCode;

  /**
   * HTTP Status codes and OpenTelemetry expected Span Status Codes for Server
   * spans.
   * 
   * @return
   */
  @Parameters()
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "99", StatusCode.ERROR },
        { "100", StatusCode.UNSET },
        { "200", StatusCode.UNSET },
        { "300", StatusCode.UNSET },
        { "400", StatusCode.UNSET },
        { "500", StatusCode.ERROR }
    });
  }

  @Test
  public void onSuccessWithResponseAttributes_getSourceEndTraceComponent() {
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

    when(event.getVariables()).thenReturn(Collections.singletonMap("httpStatus", TypedValue.of(httpStatus)));

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
    TraceComponent sourceTraceComponent = httpProcessorComponent.getSourceEndTraceComponent(
        notification,
        traceContextHandler);

    assertThat(sourceTraceComponent)
        .isNotNull()
        .extracting("name", "spanName", "statusCode").containsExactly("test-flow", null, spanStatusCode);
    assertThat(sourceTraceComponent.getTags())
        .containsEntry("http.status_code", httpStatus);
  }

}
