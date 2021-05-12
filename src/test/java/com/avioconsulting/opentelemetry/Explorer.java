package com.avioconsulting.opentelemetry;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.junit.Test;

public class Explorer {

    @Test
    public void name() {
        System.out.println();
        OpenTelemetrySdk sdk = OpenTelemetrySdkAutoConfiguration.initialize();
        GlobalMeterProvider.getMeter("avio-explorer").longValueObserverBuilder("http.requests").setUpdater(longResult -> System.currentTimeMillis());
        GlobalMeterProvider.get();
        Meter meter = GlobalMeterProvider.getMeter("avio-explorer");
        System.out.println(meter);
    }
}