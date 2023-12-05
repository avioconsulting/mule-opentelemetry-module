package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.metrics;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.*;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;

import java.util.ArrayList;
import java.util.List;

public class Java8RuntimeMetrics {
  public static void installMetrics(OpenTelemetryConnection openTelemetryConnection) {
    if (Double.parseDouble(System.getProperty("java.specification.version")) >= 17) {
      return;
    }
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(openTelemetryConnection.registerMetricsObserver(BufferPools::registerObservers));
    observables.addAll(openTelemetryConnection.registerMetricsObserver(Classes::registerObservers));
    observables.addAll(openTelemetryConnection.registerMetricsObserver(Cpu::registerObservers));
    observables.addAll(openTelemetryConnection.registerMetricsObserver(GarbageCollector::registerObservers));
    observables.addAll(openTelemetryConnection.registerMetricsObserver(MemoryPools::registerObservers));
    observables.addAll(openTelemetryConnection.registerMetricsObserver(Threads::registerObservers));

    Thread cleanupTelemetry = new Thread(() -> JmxRuntimeMetricsUtil.closeObservers(observables));
    Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
  }
}
