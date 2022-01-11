package com.avioconsulting.mule.opentelemetry.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenTelemetryStarter {

    private Logger logger = LoggerFactory.getLogger(OpenTelemetryStarter.class);

    public static final String INSTRUMENTATION_VERSION = "0.0.1";
    public static final String INSTRUMENTATION_NAME = "com.avioconsulting.mule.tracing";
    private static OpenTelemetryStarter starter;
    private OpenTelemetry openTelemetry;
    private Tracer tracer;

    private OpenTelemetryStarter() {
        logger.debug("Initialising OpenTelemetry Mule 4 Agent");
        // See here for autoconfigure options https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
        openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
        tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    public synchronized static OpenTelemetryStarter getInstance() {
        if(starter == null) {
            starter = new OpenTelemetryStarter();
        }
        return  starter;
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    public Tracer getTracer() {
        return tracer;
    }
}
