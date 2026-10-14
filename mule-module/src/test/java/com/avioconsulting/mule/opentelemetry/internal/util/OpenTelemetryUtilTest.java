package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OpenTelemetryUtilTest {

  private static final String HOST_EXPRESSION = "#[ 'local' ++ vars.region ]";
  private static final String PORT_EXPRESSION = "#[vars.dynamicConfig.port default 443]";
  private static final String FAILING_EXPRESSION = "#[vars.failed]";

  private ExpressionManager expressionManager;
  private Event event;
  private BindingContext bindingContext;

  @Before
  public void setUp() {
    expressionManager = mock(ExpressionManager.class);
    event = mock(Event.class);
    bindingContext = mock(BindingContext.class);
    when(event.asBindingContext()).thenReturn(bindingContext);
    // Mule's real DefaultExpressionManager uses String.contains without a null
    // guard.
    when(expressionManager.isExpression(any())).thenAnswer(invocation -> {
      String expression = invocation.getArgument(0);
      return expression.contains("#[");
    });
    doReturn(new TypedValue<>("localhost", DataType.STRING)).when(expressionManager)
        .evaluate(HOST_EXPRESSION, DataType.STRING, bindingContext);
    doReturn(new TypedValue<>("12345", DataType.STRING)).when(expressionManager)
        .evaluate(PORT_EXPRESSION, DataType.STRING, bindingContext);
  }

  @Test
  public void nullSpanNameDoesNotPreventResolvingTags() {
    TraceComponent trace = traceWithTags(null);
    addEndpointExpressions(trace);

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    assertThat(trace.getSpanName()).isNull();
    verify(expressionManager, never()).isExpression(isNull());
    assertResolvedEndpoint(trace);
  }

  @Test
  public void nullTagDoesNotPreventResolvingFollowingTags() {
    TraceComponent trace = traceWithTags("/target");
    trace.addTag("custom.null", null);
    addEndpointExpressions(trace);

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    LinkedHashMap<String, String> tags = new LinkedHashMap<>();
    trace.copyTagsTo(tags);
    assertThat(tags).containsEntry("custom.null", null);
    verify(expressionManager, never()).isExpression(isNull());
    assertResolvedEndpoint(trace);
  }

  @Test
  public void failedSpanNameEvaluationDoesNotPreventResolvingTags() {
    TraceComponent trace = traceWithTags(FAILING_EXPRESSION);
    addEndpointExpressions(trace);
    when(expressionManager.evaluate(FAILING_EXPRESSION, DataType.STRING, bindingContext))
        .thenThrow(new IllegalArgumentException("Span name evaluation failed"));

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    assertThat(trace.getSpanName()).isEqualTo(FAILING_EXPRESSION);
    assertResolvedEndpoint(trace);
  }

  @Test
  public void failedSpanNameClassificationDoesNotPreventResolvingTags() {
    TraceComponent trace = traceWithTags(FAILING_EXPRESSION);
    addEndpointExpressions(trace);
    when(expressionManager.isExpression(FAILING_EXPRESSION))
        .thenThrow(new IllegalArgumentException("Span name classification failed"));

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    assertThat(trace.getSpanName()).isEqualTo(FAILING_EXPRESSION);
    assertResolvedEndpoint(trace);
  }

  @Test
  public void failedTagClassificationDoesNotPreventResolvingFollowingTags() {
    TraceComponent trace = traceWithTags("/target");
    trace.addTag("custom.failed", FAILING_EXPRESSION);
    addEndpointExpressions(trace);
    when(expressionManager.isExpression(FAILING_EXPRESSION))
        .thenThrow(new IllegalArgumentException("Tag classification failed"));

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    assertThat(trace.getTag("custom.failed")).isEqualTo(FAILING_EXPRESSION);
    assertResolvedEndpoint(trace);
  }

  @Test
  public void failedTagEvaluationDoesNotPreventResolvingFollowingTags() {
    TraceComponent trace = traceWithTags("/target");
    trace.addTag("custom.failed", FAILING_EXPRESSION);
    addEndpointExpressions(trace);
    when(expressionManager.evaluate(FAILING_EXPRESSION, DataType.STRING, bindingContext))
        .thenThrow(new IllegalArgumentException("Tag evaluation failed"));

    OpenTelemetryUtil.resolveExpressions(trace, expressionManager, event);

    assertThat(trace.getTag("custom.failed")).isEqualTo(FAILING_EXPRESSION);
    assertResolvedEndpoint(trace);
  }

  private TraceComponent traceWithTags(String spanName) {
    // Preserve order so each failing/null tag precedes the valid expressions.
    return TraceComponent.of("http-request", new LinkedHashMap<>()).withSpanName(spanName);
  }

  private void addEndpointExpressions(TraceComponent trace) {
    trace.addTag("server.address", HOST_EXPRESSION);
    trace.addTag("server.port", PORT_EXPRESSION);
  }

  private void assertResolvedEndpoint(TraceComponent trace) {
    assertThat(trace.getTag("server.address")).isEqualTo("localhost");
    assertThat(trace.getTag("server.port")).isEqualTo("12345");
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    OpenTelemetryUtil.tagsToAttributes(trace, spanBuilder);
    verify(spanBuilder).setAttribute(AttributeKey.stringKey("server.address"), "localhost");
    verify(spanBuilder).setAttribute(AttributeKey.longKey("server.port"), Long.valueOf(12345L));
    verify(expressionManager).evaluate(HOST_EXPRESSION, DataType.STRING, bindingContext);
    verify(expressionManager).evaluate(PORT_EXPRESSION, DataType.STRING, bindingContext);
  }
}
