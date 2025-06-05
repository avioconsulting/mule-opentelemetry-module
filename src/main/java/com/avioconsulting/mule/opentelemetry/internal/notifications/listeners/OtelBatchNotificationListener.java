package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.ee.batch.api.notifications.OtelBatchNotification;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.avioconsulting.mule.opentelemetry.ee.batch.api.notifications.OtelBatchNotification.*;

public class OtelBatchNotificationListener extends AbstractMuleNotificationListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(OtelBatchNotificationListener.class);

  public OtelBatchNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  public void onOtelBatchNotification(OtelBatchNotification batchNotification) {
    LOGGER.trace("Batch notification received: {}, Step: {}, Record: {}", batchNotification.getActionName(),
        batchNotification.getStep() == null ? "null" : batchNotification.getStep().getName(),
        batchNotification.getRecord() == null ? "null" : batchNotification.getRecord().getCurrentStepId());
    int action = Integer.parseInt(batchNotification.getAction().getIdentifier());
    if (STEP_JOB_END == action) {
      muleNotificationProcessor.handleBatchStepEndEvent(batchNotification);
    } else if (STEP_RECORD_END == action || STEP_RECORD_FAILED == action) {
      muleNotificationProcessor.handleBatchStepRecordEndEvent(batchNotification);
    } else if (ON_COMPLETE_END == action || ON_COMPLETE_FAILED == action) {
      muleNotificationProcessor.handleBatchOnCompleteEndEvent(batchNotification);
    } else if (JOB_SUCCESSFUL == action || JOB_STOPPED == action) {
      muleNotificationProcessor.handleBatchEndEvent(batchNotification);
    }
  }
}
