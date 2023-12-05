package com.avioconsulting.mule.opentelemetry.api.config;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class SpanProcessorConfigurationTest {

  @Test
  public void getSpanProcessorConfigMap() {
    SpanProcessorConfiguration spc = new SpanProcessorConfiguration(1, 2, 3, 4);
    assertThat(spc.getConfigMap())
        .as("Span Processor Configuration Map")
        .containsEntry("otel.bsp.schedule.delay", "3")
        .containsEntry("otel.bsp.max.queue.size", "1")
        .containsEntry("otel.bsp.max.export.batch.size", "2")
        .containsEntry("otel.bsp.export.timeout", "4");
  }

  @Test
  public void testEquals() {
    SpanProcessorConfiguration spc1 = new SpanProcessorConfiguration(1, 2, 3, 4);
    SpanProcessorConfiguration spc2 = new SpanProcessorConfiguration(1, 2, 3, 4);
    SpanProcessorConfiguration spc3 = new SpanProcessorConfiguration(2, 2, 3, 4);
    assertThat(spc1).hasSameHashCodeAs(spc2).isEqualTo(spc2);
    assertThat(spc1).doesNotHaveSameHashCodeAs(spc3).isNotEqualTo(spc3);
  }

}