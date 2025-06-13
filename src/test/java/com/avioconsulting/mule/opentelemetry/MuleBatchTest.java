package com.avioconsulting.mule.opentelemetry;

import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleBatchTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "batch-config.xml";
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
  public void batchWithSpanAllTest() throws Exception {
    CoreEvent coreEvent = flowRunner("main-flow")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(300));
  }
}
