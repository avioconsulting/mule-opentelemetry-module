package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryEnabledInterceptorHttpTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  @Override
  protected String getConfigFile() {
    return "mule-interceptor-tests.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("mule.otel.span.processors.enable", "true");
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
    System.clearProperty("mule.otel.span.processors.enable");
  }

  @Test
  public void testInterceptorFlowVariableInjection() throws Exception {
    CoreEvent event = flowRunner("mule-opentelemetry-app-2-interceptor-test")
        .withSourceCorrelationId("test-correlation-id").run();
    assertThat(event.getVariables())
        .containsKey(TransactionStore.TRACE_CONTEXT_MAP_KEY)
        .extractingByKey(TransactionStore.TRACE_CONTEXT_MAP_KEY,
            as(InstanceOfAssertFactories.type(TypedValue.class)))
        .extracting("value", as(InstanceOfAssertFactories.map(String.class, String.class)))
        .containsEntry(TransactionStore.TRACE_TRANSACTION_ID, "test-correlation-id")
        .containsKey("traceparent")
        .containsKey(TransactionStore.SPAN_ID);
  }

  @Test
  public void testInterceptorFlowVariableHTTPInjection() throws Exception {
    CoreEvent event = flowRunner("mule-opentelemetry-app-2-interceptor-test-http")
        .withSourceCorrelationId("test-correlation-id").run();
    assertThat(event.getVariables())
        .containsKey(TransactionStore.TRACE_CONTEXT_MAP_KEY)
        .extractingByKey(TransactionStore.TRACE_CONTEXT_MAP_KEY,
            as(InstanceOfAssertFactories.type(TypedValue.class)))
        .extracting("value", as(InstanceOfAssertFactories.map(String.class, String.class)))
        .containsEntry(TransactionStore.TRACE_TRANSACTION_ID, "test-correlation-id")
        .containsKey("traceparent")
        .containsKey(TransactionStore.SPAN_ID);
  }

  @Test
  public void testInterceptorFlowVariableHttpInjection() throws Exception {
    CoreEvent event = flowRunner("mule-opentelemetry-app-2-interceptor-test-http")
        .withSourceCorrelationId("test-correlation-id").run();

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request flow ref")
              .extracting("spanName", "spanKind")
              .containsOnly("/test-remote-request-1", "CLIENT");
        }));
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .anySatisfy(span -> {
          assertThat(span)
              .as("Span for http:request sub-flow ref")
              .extracting("spanName", "spanKind")
              .containsOnly("/test-remote-request-2", "CLIENT");
        }));
  }

  @Test
  public void testInterceptorFlowVariableReset() throws Exception {
    CoreEvent event = flowRunner("intercept-flow-variable-reset")
        .withSourceCorrelationId("test-correlation-id").run();
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue).hasSize(6));
    DelegatedLoggingSpanTestExporter.Span mainFlow = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("intercept-flow-variable-reset")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span beforeLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:before-flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span flowRef = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("flow-ref:flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span afterLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:before-flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span targetFlow = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("flow-ref-target-flow")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span targetLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:target-logger")).findFirst().get();

    assertThat(beforeLogger.getParentSpanContext())
        .as("Parent Span Context of a logger before flow-ref")
        .describedAs("Parent span should be of main flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

    assertThat(flowRef.getParentSpanContext())
        .as("Parent Span Context of a flow-ref in main flow")
        .describedAs("Parent span should be of main flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

    assertThat(targetFlow.getParentSpanContext())
        .as("Parent Span Context of a target flow invoked by flow-ref")
        .describedAs("Parent span should be span of flow-ref")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), flowRef.getSpanId());

    assertThat(targetLogger.getParentSpanContext())
        .as("Parent Span Context of a target logger of target flow")
        .describedAs("Parent span should be span of target flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), targetFlow.getSpanId());

    assertThat(afterLogger.getParentSpanContext())
        .as("Parent Span Context of a after logger in main flow")
        .describedAs(
            "Parent span should be span of main flow again since flow-ref's context variable will reset after flow-ref execution")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

  }

  @Test
  public void testInterceptorFlowRefPropagation() throws Exception {
    CoreEvent event = flowRunner("intercept-subflow-flowref-context-propagation")
        .withSourceCorrelationId("test-correlation-id").run();
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue).hasSize(6));
    DelegatedLoggingSpanTestExporter.Span mainFlow = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("intercept-subflow-flowref-context-propagation")).findFirst()
        .get();
    DelegatedLoggingSpanTestExporter.Span beforeLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:before-flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span flowRef = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("flow-ref:flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span afterLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:after-flow-ref")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span targetFlow = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("flow-ref-target-subflow")).findFirst().get();
    DelegatedLoggingSpanTestExporter.Span targetLogger = DelegatedLoggingSpanTestExporter.spanQueue.stream()
        .filter(span -> span.getSpanName().equals("logger:target-logger")).findFirst().get();

    assertThat(beforeLogger.getParentSpanContext())
        .as("Parent Span Context of a logger before flow-ref")
        .describedAs("Parent span should be of main flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

    assertThat(flowRef.getParentSpanContext())
        .as("Parent Span Context of a flow-ref in main flow")
        .describedAs("Parent span should be of main flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

    assertThat(targetFlow.getParentSpanContext())
        .as("Parent Span Context of a target flow invoked by flow-ref")
        .describedAs("Parent span should be span of flow-ref")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), flowRef.getSpanId());

    assertThat(targetLogger.getParentSpanContext())
        .as("Parent Span Context of a target logger of target flow")
        .describedAs("Parent span should be span of target flow")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), targetFlow.getSpanId());

    assertThat(afterLogger.getParentSpanContext())
        .as("Parent Span Context of a after logger in main flow")
        .describedAs(
            "Parent span should be span of main flow again since flow-ref's context variable will reset after flow-ref execution")
        .extracting("traceId", "spanId")
        .containsOnly(mainFlow.getTraceId(), mainFlow.getSpanId());

  }
}
