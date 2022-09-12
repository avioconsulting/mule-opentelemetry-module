package com.avioconsulting.mule.opentelemetry.api.config;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpanProcessorConfigurationTest {

  @Test
  public void getSpanProcessorConfigMap() {
    SpanProcessorConfiguration spc = new SpanProcessorConfiguration(1, 2, 3, 4);
    Assertions.assertThat(spc.getConfigMap())
        .as("Span Processor Configuration Map")
        .containsEntry("otel.bsp.schedule.delay", "3")
        .containsEntry("otel.bsp.max.queue.size", "1")
        .containsEntry("otel.bsp.max.export.batch.size", "2")
        .containsEntry("otel.bsp.export.timeout", "4");
  }
}