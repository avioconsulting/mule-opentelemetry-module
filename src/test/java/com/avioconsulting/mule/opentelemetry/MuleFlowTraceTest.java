package com.avioconsulting.mule.opentelemetry;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;


@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = {
        "com.avioconsulting:open-telemetry-mule4-agent",
        "io.opentelemetry:opentelemetry-api",
        "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure",
        "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi",
        "io.opentelemetry:opentelemetry-sdk",
        "io.opentelemetry:opentelemetry-semconv",
        "io.opentelemetry:opentelemetry-sdk-common",
        "io.opentelemetry:opentelemetry-context",
        "io.opentelemetry:opentelemetry-sdk-metrics",
        "io.opentelemetry:opentelemetry-api-metrics",
        "io.opentelemetry:opentelemetry-sdk-trace",
        "io.opentelemetry:opentelemetry-exporter-otlp",
        "io.opentelemetry:opentelemetry-exporter-zipkin",
        "io.zipkin.zipkin2:zipkin",
        "io.zipkin.reporter2:zipkin-reporter",
        "io.zipkin.reporter2:zipkin-sender-okhttp3",
        "com.squareup.okio:okio",
        "com.squareup.okhttp3:okhttp"

})
public class MuleFlowTraceTest extends MuleArtifactFunctionalTestCase {
    protected static final java.util.Queue<CoreEvent> CAPTURED = new ConcurrentLinkedDeque<>();
    @Rule
    public DynamicPort serverPort = new DynamicPort("http.port");

    @Override
    protected String getConfigFile() {
        return "SimpleFlowTest.xml";
    }


    @Override
    protected void doSetUpBeforeMuleContextCreation() throws Exception {
        super.doSetUpBeforeMuleContextCreation();
        System.setProperty(TEST_TIMEOUT_SYSTEM_PROPERTY, "120_000_000");
    }


    protected CoreEvent getCapturedEvent(long timeout, String failureDescription) {
        AtomicReference<CoreEvent> value = new AtomicReference<>();
        new PollingProber(timeout, 100)
                .check(
                        new JUnitLambdaProbe(
                                () -> {
                                    synchronized (CAPTURED) {
                                        CoreEvent capturedEvent = CAPTURED.poll();
                                        if (capturedEvent != null) {
                                            value.set(capturedEvent);
                                            return true;
                                        }
                                        return false;
                                    }
                                },
                                failureDescription));

        return value.get();
    }

    @Test
    public void flowTest() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        sendRequest(correlationId, "dummyUser");
//        getCapturedEvent(1_200_000, "Fail");
    }
    @Test
    public void exceptionFlowTest() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        sendRequest(correlationId, "exception");
//        getCapturedEvent(1_200_000, "Fail");
    }

    private void sendRequest(String correlationId, String path) throws IOException {
        HttpGet getRequest = new HttpGet(String.format("http://localhost:%s/test/" + path, serverPort.getValue()));
        getRequest.addHeader("X-CORRELATION-ID", correlationId);
//        getRequest.addHeader("traceparent", "00-3e864597bcb2431935133b0dec678ed4-f75931b2493ab2b2-00");
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                assertThat(IOUtils.toString(response.getEntity().getContent())).isEqualTo("Done");
            }
        }
    }
}