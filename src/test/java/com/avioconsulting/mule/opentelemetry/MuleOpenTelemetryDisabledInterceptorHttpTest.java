package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.junit4.rule.DynamicPort;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.MessageProcessorTracingInterceptorFactory.MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class MuleOpenTelemetryDisabledInterceptorHttpTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME,
        "false");
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    super.doTearDownAfterMuleContextDispose();
    System.clearProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME);
  }

  @Test
  public void testDisabledHttpTracing() throws Exception {
    CoreEvent event = flowRunner("mule-opentelemetry-app-2-interceptor-test").run();
    assertThat(event.getVariables()).doesNotContainKey(TransactionStore.TRACE_CONTEXT_MAP_KEY);
  }
}
