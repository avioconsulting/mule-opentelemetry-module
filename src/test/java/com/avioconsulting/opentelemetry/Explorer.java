package com.avioconsulting.opentelemetry;

import com.avioconsulting.opentelemetry.spans.SpanRegistrationUtility;
import io.opentelemetry.api.trace.Span;
import org.junit.Test;

public class Explorer {

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
}