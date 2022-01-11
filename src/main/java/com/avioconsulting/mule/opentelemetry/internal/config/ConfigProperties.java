package com.avioconsulting.mule.opentelemetry.internal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigProperties {

    @Value("otel.mule.tracer.allprocessors")
    private boolean traceAllProcessors;

    public boolean traceAllProcessors() {
        return traceAllProcessors;
    }
}
