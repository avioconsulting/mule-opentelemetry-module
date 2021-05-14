package com.avioconsulting.opentelemetry;

import io.opentelemetry.api.trace.Span;
import org.junit.Test;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = {
        "com.avioconsulting:open-telemetry-mule4-agent",
        "io.opentelemetry:opentelemetry-api",
        "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure",
        "io.opentelemetry:opentelemetry-sdk",
        "io.opentelemetry:opentelemetry-sdk-common",
        "io.opentelemetry:opentelemetry-semconv",
        "io.opentelemetry:opentelemetry-context",
        "io.opentelemetry:opentelemetry-sdk-metrics",
        "io.opentelemetry:opentelemetry-api-metrics",
        "io.opentelemetry:opentelemetry-sdk-trace",
        "io.opentelemetry:opentelemetry-exporter-logging"})
public class Explorer extends MuleArtifactFunctionalTestCase {

    @Override
    protected String getConfigFile() {
        return "SimpleFlowTest.xml";
    }

    @Test
    public void flowTest() throws Exception {
        flowRunner("dep-testFlow").run();
        Thread.sleep(1000);
        System.out.println();
    }
}