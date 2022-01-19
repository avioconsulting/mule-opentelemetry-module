package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.test.util.Span;
import com.avioconsulting.mule.opentelemetry.test.util.TestLoggerHandler;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class MuleOpenTelemetryProcessorEnabled extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-processor-enabled.xml";
  }

  @Test
  public void testProcessorTracing() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "test", 200);
    assertThat(Span.fromStrings(loggerHandler.getCapturedLogs()))
        .as("Spans for listener and processors")
        .hasSize(4)
        .extracting("spanName", "spanKind")
        .containsOnly(tuple("'logger'", "INTERNAL"),
            tuple("'set-payload'", "INTERNAL"),
            tuple("'logger'", "INTERNAL"),
            tuple("'/test'", "SERVER"));
  }
}
