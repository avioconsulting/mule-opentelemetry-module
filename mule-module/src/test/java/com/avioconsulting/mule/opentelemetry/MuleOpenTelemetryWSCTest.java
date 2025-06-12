package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryWSCTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "wsc-flow-test.xml";
  }

  @Test
  public void testWSCTracing() throws Exception {
    Throwable throwable = catchThrowable(() -> flowRunner("consume-wsc-flow").run());
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .hasSize(2));
    assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .filteredOnAssertions(span -> assertThat(span)
            .as("Span for wsc:consume")
            .extracting("spanName", "spanKind")
            .containsOnly("Calculator:Add", "CLIENT"))
        .isNotEmpty()
        .hasSize(1)
        .element(0)
        .extracting("attributes", as(InstanceOfAssertFactories.map(String.class, Object.class)))
        .hasSizeGreaterThanOrEqualTo(6)
        .containsEntry("mule.wsc.consumer.operation", "Add")
        .containsEntry("mule.app.processor.name", "consume")
        .containsEntry("mule.app.processor.namespace", "wsc")
        .containsEntry("mule.wsc.config.port", "CalculatorSoap12")
        .containsEntry("mule.wsc.config.address", "http://localhost/calculator.asmx")
        .containsEntry("mule.wsc.config.service", "Calculator");
  }

}
