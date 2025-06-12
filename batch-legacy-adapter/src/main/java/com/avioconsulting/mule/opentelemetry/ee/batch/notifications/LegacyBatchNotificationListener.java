package com.avioconsulting.mule.opentelemetry.ee.batch.notifications;

import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy.BatchJobInstanceAdapter;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy.BatchStepAdapter;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy.LegacyBatchUtil;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy.RecordAdapter;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.AbstractOtelBatchNotificationListener;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotification;
import com.mulesoft.mule.runtime.module.batch.api.notification.BatchNotification;
import com.mulesoft.mule.runtime.module.batch.api.notification.BatchNotificationListener;
import org.mule.runtime.api.notification.CustomNotification;
import org.mule.runtime.api.notification.NotificationListenerRegistry;

import java.util.Objects;
import java.util.function.Consumer;

public class LegacyBatchNotificationListener extends AbstractOtelBatchNotificationListener
    implements BatchNotificationListener {

  private Consumer<OtelBatchNotification> callback;

  @Override
  protected void registerActions() {
    if (OtelBatchNotification.actionInitialized)
      return;
    OtelBatchNotification.LOAD_PHASE_BEGIN = BatchNotification.LOAD_PHASE_BEGIN;
    OtelBatchNotification.LOAD_PHASE_PROGRESS = BatchNotification.LOAD_PHASE_PROGRESS;
    OtelBatchNotification.LOAD_PHASE_END = BatchNotification.LOAD_PHASE_END;
    OtelBatchNotification.LOAD_PHASE_FAILED = BatchNotification.LOAD_PHASE_FAILED;
    OtelBatchNotification.JOB_PROCESS_RECORDS_BEGIN = BatchNotification.JOB_PROCESS_RECORDS_BEGIN;
    OtelBatchNotification.JOB_PROCESS_RECORDS_FAILED = BatchNotification.JOB_PROCESS_RECORDS_FAILED;
    OtelBatchNotification.JOB_SUCCESSFUL = BatchNotification.JOB_SUCCESSFUL;
    OtelBatchNotification.JOB_STOPPED = BatchNotification.JOB_STOPPED;
    OtelBatchNotification.JOB_CANCELLED = BatchNotification.JOB_CANCELLED;
    OtelBatchNotification.ON_COMPLETE_BEGIN = BatchNotification.ON_COMPLETE_BEGIN;
    OtelBatchNotification.ON_COMPLETE_END = BatchNotification.ON_COMPLETE_END;
    OtelBatchNotification.ON_COMPLETE_FAILED = BatchNotification.ON_COMPLETE_FAILED;
    OtelBatchNotification.STEP_RECORD_START = BatchNotification.STEP_RECORD_START;
    OtelBatchNotification.STEP_RECORD_END = BatchNotification.STEP_RECORD_END;
    OtelBatchNotification.STEP_RECORD_FAILED = BatchNotification.STEP_RECORD_FAILED;
    OtelBatchNotification.STEP_AGGREGATOR_START = BatchNotification.STEP_AGGREGATOR_START;
    OtelBatchNotification.STEP_AGGREGATOR_END = BatchNotification.STEP_AGGREGATOR_END;
    OtelBatchNotification.STEP_AGGREGATOR_FAILED = BatchNotification.STEP_AGGREGATOR_FAILED;
    OtelBatchNotification.STEP_JOB_END = BatchNotification.STEP_JOB_END;
    OtelBatchNotification.PROGRESS_UPDATE = BatchNotification.PROGRESS_UPDATE;
    OtelBatchNotification.actionInitialized = true;
  }

  @Override
  public void onNotification(CustomNotification notification) {
    if (!(notification instanceof BatchNotification))
      return;
    BatchNotification batchNotification = (BatchNotification) notification;
    Objects.requireNonNull(callback, "Callback cannot be null");
    OtelBatchNotification otelBatchNotification = new OtelBatchNotification(batchNotification,
        BatchJobInstanceAdapter.from(batchNotification.getJobInstance()),
        BatchStepAdapter.from(batchNotification.getStep()),
        batchNotification.getException(),
        RecordAdapter.from(batchNotification.getRecord()),
        batchNotification.getCorrelationId());
    callback.accept(otelBatchNotification);
  }

  @Override
  public BatchUtil getBatchUtil() {
    return new LegacyBatchUtil();
  }

  @Override
  public void register(Consumer<OtelBatchNotification> callback, NotificationListenerRegistry registry) {
    Objects.requireNonNull(callback, "Callback cannot be null");
    this.callback = callback;
    registry.registerListener(this);
  }
}
