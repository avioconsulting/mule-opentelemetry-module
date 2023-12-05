package com.avioconsulting.mule.opentelemetry.internal.notifications;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class MetricNotificationActionProviderTest {

  @Test
  public void getNotificationActions() {
    assertThat(new MetricNotificationActionProvider().getNotificationActions())
        .containsExactly(MetricNotificationAction.CUSTOM_METRIC);
  }
}