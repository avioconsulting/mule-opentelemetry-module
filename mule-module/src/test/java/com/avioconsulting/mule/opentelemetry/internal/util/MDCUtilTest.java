package com.avioconsulting.mule.opentelemetry.internal.util;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.TRACE_CONTEXT_MAP_KEY;
import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.spanId;
import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.traceId;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MDCUtilTest {

  @Before
  public void setUp() {
    MDC.clear();
  }

  @After
  public void tearDown() {
    MDC.clear();
  }

  @Test
  public void replaceMDCOtelEntriesShouldReplaceTraceAndSpanIdsWhenContextContainsValidValues() {
    Map<String, Object> context = new HashMap<>();
    context.put(traceId, "5f9a6f7d22f5f292c9db9f8f1e23df0c");
    context.put(spanId, "0af7651916cd43dd");

    MDCUtil.replaceMDCOtelEntries(context);

    Assertions.assertThat(MDC.get(MDCUtil.TRACE_ID)).isEqualTo("5f9a6f7d22f5f292c9db9f8f1e23df0c");
    Assertions.assertThat(MDC.get(MDCUtil.SPAN_ID)).isEqualTo("0af7651916cd43dd");
  }

  @Test
  public void replaceMDCOtelEntriesShouldNotReplaceExistingMdcWhenContextContainsInvalidValues() {
    Map<String, Object> context = new HashMap<>();
    context.put(traceId, TraceId.getInvalid());
    context.put(spanId, SpanId.getInvalid());
    MDC.put(MDCUtil.TRACE_ID, "existing-trace");
    MDC.put(MDCUtil.SPAN_ID, "existing-span");

    MDCUtil.replaceMDCOtelEntries(context);

    Assertions.assertThat(MDC.get(MDCUtil.TRACE_ID)).isEqualTo("existing-trace");
    Assertions.assertThat(MDC.get(MDCUtil.SPAN_ID)).isEqualTo("existing-span");
  }

  @Test
  public void replaceMDCOtelEntriesFromVarsShouldReadContextFromMuleVariables() {
    Map<String, Object> context = new HashMap<>();
    context.put(traceId, "6f8a7f7d22f5f292c9db9f8f1e23df0d");
    context.put(spanId, "1bf7651916cd43de");
    Map<String, TypedValue<?>> variables = new HashMap<>();
    variables.put(TRACE_CONTEXT_MAP_KEY, TypedValue.of(context));

    MDCUtil.replaceMDCOtelEntriesFromVars(variables);

    Assertions.assertThat(MDC.get(MDCUtil.TRACE_ID)).isEqualTo("6f8a7f7d22f5f292c9db9f8f1e23df0d");
    Assertions.assertThat(MDC.get(MDCUtil.SPAN_ID)).isEqualTo("1bf7651916cd43de");
  }

  @Test
  public void replaceMDCOtelEntriesWithEventShouldHandleNullEvent() {
    MDCUtil.replaceMDCOtelEntries((Event) null);

    Assertions.assertThat(MDC.getCopyOfContextMap()).isEmpty();
  }

  @Test
  public void replaceMDCOtelEntriesWithEventShouldUseEventVariables() {
    Map<String, Object> context = new HashMap<>();
    context.put(traceId, "7f8a7f7d22f5f292c9db9f8f1e23df0e");
    context.put(spanId, "2cf7651916cd43df");
    Map<String, TypedValue<?>> variables = Collections.singletonMap(TRACE_CONTEXT_MAP_KEY, TypedValue.of(context));
    Event event = mock(Event.class);
    when(event.getVariables()).thenReturn(variables);

    MDCUtil.replaceMDCOtelEntries(event);

    Assertions.assertThat(MDC.get(MDCUtil.TRACE_ID)).isEqualTo("7f8a7f7d22f5f292c9db9f8f1e23df0e");
    Assertions.assertThat(MDC.get(MDCUtil.SPAN_ID)).isEqualTo("2cf7651916cd43df");
  }
}
