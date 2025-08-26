package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.trace.Span;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedNotificationInfo;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class MuleNotificationProcessorContextExtractorTest extends AbstractProcessorComponentTest {

  private ComponentRegistryService componentRegistryService = mock(ComponentRegistryService.class);

  @Parameterized.Parameter(value = 0)
  public static String expressionText;

  @Parameterized.Parameters()
  public static Collection<String> data() {
    return Arrays.asList("#[attributes.headers]",
        "#[attributes.properties]",
        "#[attributes.properties.userProperties]", "#[payload.message.messageAttributes]");
  }

  @Test
  public void handleFlowStartEvent_generic_context_extraction() throws InitialisationException {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    EventContext eventContext = mock(EventContext.class);
    when(eventContext.getId()).thenReturn("testEventContextId");
    when(event.getContext()).thenReturn(eventContext);
    BindingContext bindingContext = mock(BindingContext.class);
    when(event.asBindingContext()).thenReturn(bindingContext);

    Map<String, Object> contextHolderMap = new HashMap<>();
    contextHolderMap.put("traceparent", "00-436b61977bfcd902672d651c1b84e2f3-624994541baf73d4-01");

    Map<String, Object> placeholder = new HashMap<>();
    Message message = getMessage(placeholder);
    when(event.getMessage()).thenReturn(message);
    String flowName = "mule-sample-flow";
    ComponentLocation componentLocation = getFlowLocation(flowName);
    Component component = getComponent(componentLocation, Collections.emptyMap(), "mule", "flow");
    Exception exception = mock(Exception.class);

    EnrichedNotificationInfo info = EnrichedNotificationInfo.createInfo(event, exception, component);
    PipelineMessageNotification pipelineMessageNotification = new PipelineMessageNotification(info, flowName,
        PipelineMessageNotification.PROCESS_START);

    OpenTelemetryConnection connection = getOpenTelemetryConnection();
    ExpressionManager expressionManager = mock(ExpressionManager.class);
    TypedValue<?> tTypedValue = TypedValue.of(contextHolderMap);
    doReturn(tTypedValue).when(expressionManager).evaluate(eq(expressionText), any(BindingContext.class));
    doReturn(expressionManager).when(connection).getExpressionManager();

    ArgumentCaptor<TraceComponent> captor = ArgumentCaptor.forClass(TraceComponent.class);
    doNothing().when(connection).startTransaction(captor.capture());
    ComponentWrapper wrapper = new ComponentWrapper(component, componentRegistryService);
    when(componentRegistryService.getComponentWrapper(component)).thenReturn(wrapper);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(componentRegistryService);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleFlowStartEvent(pipelineMessageNotification);

    TraceComponent value = captor.getValue();
    assertThat(value).isNotNull();

    Span span = Span.fromContext(value.getContext());
    assertThat(span.getSpanContext())
        .isNotNull()
        .extracting("traceId", "spanId")
        .as("Context extracted from " + expressionText)
        .containsExactlyInAnyOrder("436b61977bfcd902672d651c1b84e2f3", "624994541baf73d4");
  }

  @Test
  public void handleFlowStartEvent_generic_context_extraction_bytearray() throws InitialisationException {
    Event event = mock(Event.class);
    when(event.getCorrelationId()).thenReturn("testCorrelationId");
    EventContext eventContext = mock(EventContext.class);
    when(eventContext.getId()).thenReturn("testEventContextId");
    when(event.getContext()).thenReturn(eventContext);
    BindingContext bindingContext = mock(BindingContext.class);
    when(event.asBindingContext()).thenReturn(bindingContext);

    Map<String, Object> contextHolderMap = new HashMap<>();
    contextHolderMap.put("traceparent", "00-436b61977bfcd902672d651c1b84e2f3-624994541baf73d4-01".getBytes());

    Map<String, Object> placeholder = new HashMap<>();
    Message message = getMessage(placeholder);
    when(event.getMessage()).thenReturn(message);
    String flowName = "mule-sample-flow";
    ComponentLocation componentLocation = getFlowLocation(flowName);
    Component component = getComponent(componentLocation, Collections.emptyMap(), "mule", "flow");
    Exception exception = mock(Exception.class);

    EnrichedNotificationInfo info = EnrichedNotificationInfo.createInfo(event, exception, component);
    PipelineMessageNotification pipelineMessageNotification = new PipelineMessageNotification(info, flowName,
        PipelineMessageNotification.PROCESS_START);

    OpenTelemetryConnection connection = getOpenTelemetryConnection();
    ExpressionManager expressionManager = mock(ExpressionManager.class);
    TypedValue<?> tTypedValue = TypedValue.of(contextHolderMap);
    doReturn(tTypedValue).when(expressionManager).evaluate(eq(expressionText), any(BindingContext.class));
    doReturn(expressionManager).when(connection).getExpressionManager();

    ArgumentCaptor<TraceComponent> captor = ArgumentCaptor.forClass(TraceComponent.class);
    doNothing().when(connection).startTransaction(captor.capture());
    ComponentWrapper wrapper = new ComponentWrapper(component, componentRegistryService);
    when(componentRegistryService.getComponentWrapper(component)).thenReturn(wrapper);
    MuleNotificationProcessor notificationProcessor = new MuleNotificationProcessor(
        componentRegistryService);
    notificationProcessor.init(connection, new TraceLevelConfiguration(false, Collections.emptyList()));
    notificationProcessor.handleFlowStartEvent(pipelineMessageNotification);

    TraceComponent value = captor.getValue();
    assertThat(value).isNotNull();

    Span span = Span.fromContext(value.getContext());
    assertThat(span.getSpanContext())
        .isNotNull()
        .extracting("traceId", "spanId")
        .as("Context extracted from " + expressionText)
        .containsExactlyInAnyOrder("436b61977bfcd902672d651c1b84e2f3", "624994541baf73d4");
  }

}