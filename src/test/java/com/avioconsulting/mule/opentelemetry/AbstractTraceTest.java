package com.avioconsulting.mule.opentelemetry;

import org.junit.Before;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

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
        "io.opentelemetry:opentelemetry-exporter-logging",
        "io.opentelemetry:opentelemetry-exporter-zipkin",
        "io.zipkin.zipkin2:zipkin",
        "io.zipkin.reporter2:zipkin-reporter",
        "io.zipkin.reporter2:zipkin-sender-okhttp3",
        "com.squareup.okio:okio",
        "com.squareup.okhttp3:okhttp",
        "com.google.guava:guava"
})
public abstract class AbstractTraceTest  extends MuleArtifactFunctionalTestCase {

    protected static final java.util.Queue<CoreEvent> CAPTURED = new ConcurrentLinkedDeque<>();

    @Override
    protected void doSetUpBeforeMuleContextCreation() throws Exception {
        super.doSetUpBeforeMuleContextCreation();
        System.setProperty(TEST_TIMEOUT_SYSTEM_PROPERTY, "120_000_000");
        System.setProperty("otel.traces.exporter", "logging");
        System.setProperty("otel.resource.attributes", "deployment.environment=test,service.name=test-flows");
        System.setProperty("otel.metrics.exporter", "none");
    }

    @Before
    public void beforeTest(){
        System.setProperty("otel.traces.exporter", "logging");
    }

    protected void withZipkinExporter(){
        System.setProperty("otel.traces.exporter", "zipkin");
        System.setProperty("otel.exporter.zipkin.endpoint","http://localhost:9411/api/v2/spans");
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

}
