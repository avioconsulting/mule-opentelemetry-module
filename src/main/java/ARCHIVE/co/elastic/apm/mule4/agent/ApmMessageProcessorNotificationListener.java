package ARCHIVE.co.elastic.apm.mule4.agent;

import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.MessageProcessorNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Listener for Mule notifications on process start, end and completion.
 */
public class ApmMessageProcessorNotificationListener
		implements MessageProcessorNotificationListener<MessageProcessorNotification> {

	private Logger logger = LoggerFactory.getLogger(ApmMessageProcessorNotificationListener.class);

	@SuppressWarnings("deprecation")
	@Override
	public void onNotification(MessageProcessorNotification notification) {
		logger.debug("===> Received " + notification.getClass().getName() + ":" + notification.getActionName());

		// Event listener
		switch (notification.getAction().getActionId()) {
		case MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE:
			ApmHandler.handleProcessorStartEvent(notification);
			break;

		case MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE:
			ApmHandler.handleProcessorEndEvent(notification);
			break;
		}
	}

}
