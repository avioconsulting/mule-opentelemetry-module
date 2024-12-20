package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter.spanQueue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MuleKafkaTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-kafka-example.xml";
  }

  // Instruction to run Kafka in local
  // https://developer.confluent.io/confluent-tutorials/kafka-on-docker/
  // TODO: Validate against in-memory kafka instance
  @Test
  @Ignore(value = "Requires a running instance of Kafka in local")
  public void testFlowControls() throws Exception {
    CoreEvent coreEvent = flowRunner("mule-kafka-publisher-flow")
        .run();
    await().untilAsserted(() -> assertThat(spanQueue)
        .hasSize(2));
    DelegatedLoggingSpanTestExporter.Span publisherSpan = getSpan("SERVER", "mule-kafka-publisher-flow");
    assertThat(publisherSpan).isNotNull();
    DelegatedLoggingSpanTestExporter.Span listenerSpan = getSpan("SERVER", "mule-kafka-listener-flow");
    assertThat(listenerSpan).isNotNull()
        .extracting("traceId")
        .isEqualTo(publisherSpan.getTraceId());
    assertThat(listenerSpan.getParentSpanContext()).isNotNull()
        .isEqualTo(publisherSpan.getSpanContext());
  }

}
