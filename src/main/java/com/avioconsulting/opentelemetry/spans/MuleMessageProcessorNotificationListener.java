package com.avioconsulting.opentelemetry.spans;

import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.MessageProcessorNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MuleMessageProcessorNotificationListener 
	implements MessageProcessorNotificationListener<MessageProcessorNotification> {

	private Logger logger = LoggerFactory.getLogger(MuleMessageProcessorNotificationListener.class);

	@SuppressWarnings("deprecation")
	@Override
	public void onNotification(MessageProcessorNotification notification) {
		logger.debug("===> Received " + notification.getClass().getName() + ":" + notification.getActionName());

		// Event listener
		switch (notification.getAction().getActionId()) {
		case MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE:
			OpenTelemetryMuleEventProcessor.handleProcessorStartEvent(notification);
			break;

		case MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE:
			OpenTelemetryMuleEventProcessor.handleProcessorEndEvent(notification);
			break;
		}
	}

}

