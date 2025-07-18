package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.api.notification.PipelineMessageNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Listener for Mule notifications on flow start, end and completion.
 */
public class MulePipelineMessageNotificationListener
    extends AbstractMuleNotificationListener<PipelineMessageNotification>
    implements PipelineMessageNotificationListener<PipelineMessageNotification> {

  private final Logger LOGGER = LoggerFactory.getLogger(MulePipelineMessageNotificationListener.class);

  public MulePipelineMessageNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    super(muleNotificationProcessor);
  }

  @Override
  protected Event getEvent(PipelineMessageNotification notification) {
    return notification.getEvent();
  }

  @Override
  protected void processNotification(PipelineMessageNotification notification) {
    switch (Integer.parseInt(notification.getAction().getIdentifier())) {
      case PipelineMessageNotification.PROCESS_START:
        muleNotificationProcessor.handleFlowStartEvent(notification);
        break;

      // On exception this event doesn't fire, only on successful flow completion.
      case PipelineMessageNotification.PROCESS_END:
        break;

      case PipelineMessageNotification.PROCESS_COMPLETE:
        muleNotificationProcessor.handleFlowEndEvent(notification);
        break;
    }
  }
}
