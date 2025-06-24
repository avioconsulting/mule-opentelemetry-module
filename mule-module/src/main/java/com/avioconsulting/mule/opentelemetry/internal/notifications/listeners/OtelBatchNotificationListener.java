package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotification;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotification.*;

public class OtelBatchNotificationListener extends AbstractMuleNotificationListener<OtelBatchNotification> {

  private static final Logger LOGGER = LoggerFactory.getLogger(OtelBatchNotificationListener.class);

  public OtelBatchNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  protected Event getEvent(OtelBatchNotification notification) {
    return null;
  }

  @Override
  protected void processNotification(OtelBatchNotification notification) {
    replaceMDCEntry(
        notification.getRecord() != null ? notification.getRecord().getAllVariables() : Collections.emptyMap());
    LOGGER.debug("Batch notification received: {}, Step: {}, Record: {}", notification.getActionName(),
        notification.getStep() == null ? "null" : notification.getStep().getName(),
        notification.getRecord() == null ? "null" : notification.getRecord().getCurrentStepId());
    int action = Integer.parseInt(notification.getAction().getIdentifier());
    if (STEP_JOB_END == action) {
      muleNotificationProcessor.handleBatchStepEndEvent(notification);
    } else if (STEP_RECORD_END == action || STEP_RECORD_FAILED == action) {
      muleNotificationProcessor.handleBatchStepRecordEndEvent(notification);
    } else if (ON_COMPLETE_END == action || ON_COMPLETE_FAILED == action) {
      muleNotificationProcessor.handleBatchOnCompleteEndEvent(notification);
    } else if (JOB_SUCCESSFUL == action || JOB_STOPPED == action) {
      muleNotificationProcessor.handleBatchEndEvent(notification);
    }
  }
}
