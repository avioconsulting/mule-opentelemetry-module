package ARCHIVE.co.elastic.apm.mule4.agent;

import org.mule.runtime.api.notification.ExceptionNotification;
import org.mule.runtime.api.notification.ExceptionNotificationListener;

public class ApmExceptionNotificationListener implements ExceptionNotificationListener {

	@Override
	public void onNotification(ExceptionNotification notification) {

		ApmHandler.handleExceptionEvent(notification);

	}

}
