package com.avioconsulting.mule.opentelemetry;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.util.Map;
import java.util.Set;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME;
import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleCoreFlowsWithoutInterceptorTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-core-flows.xml";
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
  public void testFlowRefInvocations_withCurrentContextOperations() throws Exception {
    CoreEvent event = flowRunner("root-flow").run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(11));
    Map<Object, Set<String>> groupedSpans = groupSpanByParent();
    System.out.println(groupedSpans);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(groupedSpans)
        .hasEntrySatisfying("root-flow", val -> assertThat(val)
            .containsOnly(
                "root-flow", "logger:root-flow:FirstRootLogger",
                "get-current-trace-context:root-flow:Get Current Trace Context",
                "flow-ref:root-flow:simple-flow", "simple-flow"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("simple-flow",
        val -> assertThat(val).containsOnly("flow-ref:simple-flow:simple-subflow-logger",
            "get-current-trace-context:simple-flow:Get Current Trace Context",
            "logger:simple-flow:FirstSimpleLogger"));
    softly.assertThat(groupedSpans)
        .as("Sub-flow span created from the flow-ref notification and context")
        .hasEntrySatisfying("flow-ref:simple-flow:simple-subflow-logger",
            val -> assertThat(val).containsOnly("simple-subflow-logger"));
    softly.assertThat(groupedSpans).hasEntrySatisfying("simple-subflow-logger",
        val -> assertThat(val).containsOnly(
            "get-current-trace-context:simple-subflow-logger:Get Current Trace Context",
            "logger:simple-subflow-logger:SimpleLogger"));
    softly.assertAll();
  }

}
