package com.avioconsulting.mule.opentelemetry;

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

    private static OpenTelemetry openTelemetry;
    private static Tracer tracer;

    public OpenTelemetryStarter() {
        logger.debug("Initialising OpenTelemetry Mule 4 Agent");
        // See here for autoconfigure options https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
        openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
        tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    public static OpenTelemetry getOpenTelemetry() {
        if (openTelemetry == null) {
            openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
        }
        return openTelemetry;
    }

    public static Tracer getTracer() {
        if (tracer == null) {
            if (openTelemetry == null) {
                openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
            }
            tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        }
        return tracer;
    }
}
