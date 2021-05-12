package ARCHIVE.co.elastic.apm.mule4.agent.tracing;

import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.mule.runtime.api.util.MultiMap;

import co.elastic.apm.api.Span;

public class HttpTracingUtils {

	private static final String HTTP_REQUEST_NAMESPACE = "http://www.mulesoft.org/schema/mule/http";
	private static final String HTTP_REQUEST_NAME = "request";
	public static final String ELASTIC_APM_TRACEPARENT_HEADER = "elastic-apm-traceparent";
	public static final String TRACEPARENT_HEADER = "traceparent";

	public static boolean isHttpEvent(PipelineMessageNotification notification) {
		return extractAttributes(notification).getDataType().getType() == HttpRequestAttributes.class;
	}

	public static boolean hasRemoteParent(PipelineMessageNotification notification) {

		if (!isHttpEvent(notification))
			return false;

		return getHttpAttributes(notification).containsKey(ELASTIC_APM_TRACEPARENT_HEADER)
				|| getHttpAttributes(notification).containsKey(TRACEPARENT_HEADER);
	}

	public static String getTracingHeaderValue(String x, PipelineMessageNotification notification) {

		if (getHttpAttributes(notification).containsKey(ELASTIC_APM_TRACEPARENT_HEADER))
			return getHttpAttributes(notification).get(ELASTIC_APM_TRACEPARENT_HEADER);
		else
			return getHttpAttributes(notification).get(TRACEPARENT_HEADER);
	}

	private static MultiMap<String, String> getHttpAttributes(PipelineMessageNotification notification) {

		HttpRequestAttributes attributes = (HttpRequestAttributes) extractAttributes(notification).getValue();

		return attributes.getHeaders();
	}

	private static TypedValue<Object> extractAttributes(PipelineMessageNotification notification) {
		return notification.getEvent().getMessage().getAttributes();
	}

	public static boolean isHttpRequester(MessageProcessorNotification notification) {

		ComponentIdentifier identifier = notification.getInfo().getComponent().getIdentifier();
		String name = identifier.getName();
		String namespace = identifier.getNamespaceUri();

		return HTTP_REQUEST_NAME.equals(name) && HTTP_REQUEST_NAMESPACE.equals(namespace);
	}

	public static void propagateTraceIdHeader(Span span, MessageProcessorNotification notification) {
		// TODO Auto-generated method stub

	}

}
