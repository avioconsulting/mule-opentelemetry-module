package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import org.junit.Test;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class OpenTelemetryUtilTest {

  @Test
  public void resolveExpressions_dropsUnresolvableTags() {
    // GitHub# issues/328
    ExpressionManager expressionManager = mock(ExpressionManager.class);
    when(expressionManager.isExpression(anyString()))
        .thenAnswer(invocation -> ((String) invocation.getArgument(0)).startsWith("#["));
    doReturn(new TypedValue<>("example.com", DataType.STRING)).when(expressionManager)
        .evaluate(eq("#[vars.host]"), eq(DataType.STRING), any(BindingContext.class));
    when(expressionManager.evaluate(eq("#[vars.dynamicConfig.port default 443]"), eq(DataType.STRING),
        any(BindingContext.class)))
            .thenThrow(new IllegalStateException("Expression evaluation failed"));
    Event event = mock(Event.class);
    when(event.asBindingContext()).thenReturn(mock(BindingContext.class));

    TraceComponent traceComponent = TraceComponent.of("test", new HashMap<>());
    traceComponent.addTag("server.address", "#[vars.host]");
    traceComponent.addTag("server.port", "#[vars.dynamicConfig.port default 443]");
    traceComponent.addTag("http.route", "/test");

    OpenTelemetryUtil.resolveExpressions(traceComponent, expressionManager, event);

    assertThat(traceComponent.getReadOnlyTags())
        .containsEntry("server.address", "example.com")
        .containsEntry("http.route", "/test")
        .doesNotContainKey("server.port");
  }

  @Test
  public void tagsToAttributes_skipsNonNumericValueForLongAttribute() {
    // GitHub# issues/328
    TraceComponent traceComponent = TraceComponent.of("test", new HashMap<>());
    traceComponent.addTag("server.address", "example.com");
    traceComponent.addTag("server.port", "#[vars.dynamicConfig.port default 443]");
    SpanBuilder spanBuilder = mock(SpanBuilder.class);

    OpenTelemetryUtil.tagsToAttributes(traceComponent, spanBuilder);

    verify(spanBuilder).setAttribute(AttributeKey.stringKey("server.address"), "example.com");
    verifyNoMoreInteractions(spanBuilder);
  }

  @Test
  public void tagsToAttributes_convertsNumericValueForLongAttribute() {
    TraceComponent traceComponent = TraceComponent.of("test", new HashMap<>());
    traceComponent.addTag("server.port", "8081");
    SpanBuilder spanBuilder = mock(SpanBuilder.class);

    OpenTelemetryUtil.tagsToAttributes(traceComponent, spanBuilder);

    verify(spanBuilder).setAttribute(AttributeKey.longKey("server.port"), 8081L);
  }
}
