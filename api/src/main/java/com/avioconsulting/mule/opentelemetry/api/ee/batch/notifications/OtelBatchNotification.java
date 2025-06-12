package com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstance;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import org.mule.runtime.api.notification.CustomNotification;
import org.mule.runtime.api.notification.IntegerAction;

public class OtelBatchNotification extends CustomNotification {
  private final BatchJobInstance jobInstance;
  private final BatchStep step;
  private final Exception exception;
  private final Record record;
  private final String correlationId;
  private final IntegerAction action;
  private final String actionName;
  private final CustomNotification notification;
  private final long timestamp;

  public static boolean actionInitialized = false;
  public static int LOAD_PHASE_BEGIN;
  public static int LOAD_PHASE_PROGRESS;
  public static int LOAD_PHASE_END;
  public static int LOAD_PHASE_FAILED;
  public static int JOB_PROCESS_RECORDS_BEGIN;
  public static int JOB_PROCESS_RECORDS_FAILED;
  public static int JOB_SUCCESSFUL;
  public static int JOB_STOPPED;
  public static int JOB_CANCELLED;
  public static int ON_COMPLETE_BEGIN;
  public static int ON_COMPLETE_END;
  public static int ON_COMPLETE_FAILED;
  public static int STEP_RECORD_START;
  public static int STEP_RECORD_END;
  public static int STEP_RECORD_FAILED;
  public static int STEP_AGGREGATOR_START;
  public static int STEP_AGGREGATOR_END;
  public static int STEP_AGGREGATOR_FAILED;
  public static int STEP_JOB_END;
  public static int PROGRESS_UPDATE;

  public OtelBatchNotification(CustomNotification notification, BatchJobInstance jobInstance, BatchStep step,
      Exception exception, Record record, String correlationId) {
    super(null, Integer.parseInt(notification.getAction().getIdentifier()));
    this.notification = notification;
    this.action = notification.getAction();
    String actionName = NO_ACTION_NAME;
    try {
      actionName = notification.getActionName();
    } catch (IllegalArgumentException ignored) {
      // in case some actions are not registered, eg. 300002
    }
    this.actionName = actionName;
    this.jobInstance = jobInstance;
    this.step = step;
    this.exception = exception;
    this.record = record;
    this.correlationId = correlationId;
    this.timestamp = notification.getTimestamp();
  }

  public BatchJobInstance getJobInstance() {
    return jobInstance;
  }

  public BatchStep getStep() {
    return step;
  }

  public Exception getException() {
    return exception;
  }

  public Record getRecord() {
    return record;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public IntegerAction getAction() {
    return action;
  }

  public String getActionName() {
    return actionName;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
