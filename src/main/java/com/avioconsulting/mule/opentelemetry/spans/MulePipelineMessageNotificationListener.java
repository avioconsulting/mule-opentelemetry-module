package com.avioconsulting.mule.opentelemetry.spans;

import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.api.notification.PipelineMessageNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Listener for Mule notifications on flow start, end and completion.
 */
public class MulePipelineMessageNotificationListener
		implements PipelineMessageNotificationListener<PipelineMessageNotification> {

	private Logger logger = LoggerFactory.getLogger(MulePipelineMessageNotificationListener.class);

	@Override
	public void onNotification(PipelineMessageNotification notification) {
		logger.debug("===> Received " + notification.getClass().getName() + ":" + notification.getActionName());

		switch (Integer.parseInt(notification.getAction().getIdentifier())) {
		case PipelineMessageNotification.PROCESS_START:
			OpenTelemetryMuleEventProcessor.handleFlowStartEvent(notification);
			break;

		// On exception this event doesn't fire, only on successful flow completion.
		case PipelineMessageNotification.PROCESS_END:
			break;
			
		case PipelineMessageNotification.PROCESS_COMPLETE:
			OpenTelemetryMuleEventProcessor.handleFlowEndEvent(notification);
			break;
		}
	}

}
