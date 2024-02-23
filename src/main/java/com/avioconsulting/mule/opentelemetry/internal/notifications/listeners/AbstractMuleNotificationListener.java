package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;

public abstract class AbstractMuleNotificationListener {

  protected final MuleNotificationProcessor muleNotificationProcessor;

  public AbstractMuleNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

}
