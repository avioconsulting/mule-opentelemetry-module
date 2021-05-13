package com.avioconsulting.opentelemetry;

import com.avioconsulting.opentelemetry.spans.SpanRegistrationUtility;
import io.opentelemetry.api.trace.Span;
import org.junit.Test;
import org.mule.functional.junit4.FunctionalTestCase;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = {"com.avioconsulting:open-telemetry-mule4-agent", "io.opentelemetry:opentelemetry-api"})
public class Explorer extends MuleArtifactFunctionalTestCase {

    @Override
    protected String getConfigFile() {
        return "SimpleFlowTest.xml";
    }

    @Test
    public void spansTest() throws InterruptedException {
        System.out.println("START");
        OpenTelemetryStarter openTelemetryStarter = new OpenTelemetryStarter();
        Span my_test_span = SpanRegistrationUtility.createSpan("my_test_span").startSpan();
        my_test_span.addEvent("Flow Start");
        Thread.sleep(200);
        my_test_span.addEvent("HTTP Request");
        Thread.sleep(5000);
        my_test_span.end();
        System.out.println("END");
    }

    @Test
    public void flowTest() throws Exception {
        flowRunner("dep-testFlow").run();
        Thread.sleep(1000);
        System.out.println();
    }
}