package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotificationListener;

public abstract class AbstractOtelBatchNotificationListener implements OtelBatchNotificationListener {

  public AbstractOtelBatchNotificationListener() {
    registerActions();
  }

  protected abstract void registerActions();
}
