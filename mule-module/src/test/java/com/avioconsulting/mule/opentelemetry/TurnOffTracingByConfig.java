package com.avioconsulting.mule.opentelemetry;

import org.assertj.core.api.SoftAssertions;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

public class TurnOffTracingByConfig extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "turn-off-tracing-by-config.xml";
  }

  @Override
  public void clearSpansQueue() {
    // No tracing is expected so nothing to clean
  }

  @Test
  public void testNoTracingInteraction() throws Exception {
    CoreEvent event = flowRunner("simple-flow").run();
    ConditionTimeoutException conditionTimeoutException = catchThrowableOfType(
        () -> await().timeout(Duration.ofSeconds(2)).until(() -> !spanQueue.isEmpty()),
        ConditionTimeoutException.class);
    assertThat(conditionTimeoutException).isNotNull();
  }

}
