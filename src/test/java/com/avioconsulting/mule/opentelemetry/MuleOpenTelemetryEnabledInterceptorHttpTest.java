package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

public class MuleOpenTelemetryEnabledInterceptorHttpTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
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
        .containsKey("traceparent");
  }
}
