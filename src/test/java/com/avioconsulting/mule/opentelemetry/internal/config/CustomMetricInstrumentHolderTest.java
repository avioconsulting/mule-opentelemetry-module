package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.metrics.CustomMetricInstrumentDefinition;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongCounter;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Arrays;

public class CustomMetricInstrumentHolderTest extends AbstractInternalTest {

  @Test
  public void verifyHolder() {
    CustomMetricInstrumentHolder<LongCounter> holder = new CustomMetricInstrumentHolder<LongCounter>()
        .setMetricInstrument(new CustomMetricInstrumentDefinition().setMetricName("some.test.instrument")
            .setAttributeKeys(Arrays.asList("some.attr.1", "some.attr.2")));
    Assertions.assertThat(holder.getAttributeKeys())
        .containsEntry("some.attr.1", AttributeKey.stringKey("some.attr.1"))
        .containsEntry("some.attr.2", AttributeKey.stringKey("some.attr.2"));
  }
}