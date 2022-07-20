package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.DelegatedLoggingSpanExporterProvider.DelegatedLoggingSpanExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryHttpAttributesTest extends AbstractMuleArtifactTraceTest {

    @Override
    protected String getConfigFile() {
        return "mule-http-attributes.xml";
    }

    @Test
    public void testHttpAttributes() throws Exception {
        sendRequest(CORRELATION_ID, "otel-service", "/http/sender/attributes", 200, Collections.EMPTY_MAP) ;
        await().untilAsserted(() -> assertThat(DelegatedLoggingSpanExporter.spanQueue)
                .hasSize(3)
                .element(2)
                .extracting("spanName", "spanKind", "spanStatus")
                .containsOnly("/http/sender/attributes", "SERVER", "UNSET"));
        Object q = DelegatedLoggingSpanExporter.spanQueue;
//        DelegatedLoggingSpanExporter.spanQueue.poll();
//        DelegatedLoggingSpanExporter.spanQueue.poll();
        DelegatedLoggingSpanExporterProvider.Span clientSpan = DelegatedLoggingSpanExporter.spanQueue.poll();
        assertThat(clientSpan)
                .extracting("attributes", InstanceOfAssertFactories.map(String.class, String.class))
                .containsEntry("http.status_code", "200")
                .containsEntry("http.target", "/http/receiver/attributes")
                .containsEntry("http.host", "localhost:" + serverPort.getValue())
                .containsEntry("http.scheme", "http")
                .containsEntry("http.method", "GET")
                .containsEntry("http.route", "/http/receiver/attributes")
                .containsEntry("http.url", "http://localhost:" + serverPort.getValue() + "/http/receiver/attributes");
    }
}
