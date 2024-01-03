package com.avioconsulting.mule.opentelemetry.internal.notifications;

import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class MetricNotificationActionProviderTest extends AbstractInternalTest {

  @Test
  public void getNotificationActions() {
    assertThat(new MetricNotificationActionProvider().getNotificationActions())
        .containsExactly(MetricNotificationAction.CUSTOM_METRIC);
  }
}