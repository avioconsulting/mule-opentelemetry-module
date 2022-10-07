package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ComponentTagsCachingTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-http.xml";
  }

  @Before
  @Override
  public void clearSpansQueue() {
    // Spans are removed as a part of assertion comparison in test methods.
  }

  @Test
  public void testHttpTracing_Caching() throws Exception {
    sendRequest(CORRELATION_ID, "/test/propagation/source", 200);
    sendRequest(CORRELATION_ID, "/test/propagation/source", 200);

    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .hasSizeGreaterThanOrEqualTo(6));
    assertThat(DelegatedLoggingSpanExporter.spanQueue)
        .element(0)
        .extracting("attributes", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("http.status_code", "200");
    List<DelegatedLoggingSpanExporterProvider.Span> sources = DelegatedLoggingSpanExporter.spanQueue.stream()
        .filter(s -> s.getSpanName().equalsIgnoreCase("/test/propagation/source")).collect(Collectors.toList());
    assertThat(sources.get(0).getAttributes())
        .containsExactlyEntriesOf(sources.get(1).getAttributes());
  }

}
