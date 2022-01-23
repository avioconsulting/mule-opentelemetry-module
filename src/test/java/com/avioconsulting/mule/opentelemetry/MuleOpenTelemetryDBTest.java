package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.test.util.Span;
import com.avioconsulting.mule.opentelemetry.test.util.TestLoggerHandler;
import org.junit.Test;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = { "org.apache.derby:derby" })
public class MuleOpenTelemetryDBTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "db-flows.xml";
  }

  @Test
  public void testValidDBSelectTracing() throws Exception {
    TestLoggerHandler loggerHandler = getTestLoggerHandler();
    sendRequest(UUID.randomUUID().toString(), "/test/db/select", 200);
    assertThat(Span.fromStrings(loggerHandler.getCapturedLogs()))
        .hasSize(1)
        .element(0)
        .extracting("spanName", "spanKind")
        .containsOnly("'/test/db/select'", "SERVER");
  }

}
