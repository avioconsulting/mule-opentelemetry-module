package com.avioconsulting.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenTelemetryStarter {

    private Logger logger = LoggerFactory.getLogger(OpenTelemetryStarter.class);
    private static OpenTelemetry openTelemetry;

    public OpenTelemetryStarter() {

        logger.debug("Initialising OpenTelemetry Mule 4 Agent");
        // See here for autoconfigure options https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
        openTelemetry = OpenTelemetrySdkAutoConfiguration.initialize();
    }

    public static OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
}
