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
        "io.opentelemetry:opentelemetry-exporter-logging",
        "io.opentelemetry:opentelemetry-exporter-jaeger",
        "io.opentelemetry:opentelemetry-exporter-zipkin",
        "io.grpc:grpc-api",
        "io.grpc:grpc-core",
        "io.grpc:grpc-protobuf",
        "io.grpc:grpc-stub",
        "io.grpc:grpc-context",
        "io.grpc:grpc-protobuf-lite",
        "io.perfmark:perfmark-api",
        "com.squareup.okio:okio",
        "com.google.protobuf:protobuf-java",
        "com.google.protobuf:protobuf-java-util",
        "io.grpc:grpc-okhttp",
        "com.google.guava:guava"})
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