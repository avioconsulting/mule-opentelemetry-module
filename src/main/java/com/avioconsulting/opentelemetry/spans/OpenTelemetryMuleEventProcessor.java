package com.avioconsulting.opentelemetry.spans;

import com.avioconsulting.opentelemetry.OpenTelemetryStarter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.mule.runtime.api.notification.MessageProcessorNotification;
import org.mule.runtime.api.notification.PipelineMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.Map;
import java.util.Optional;


public class OpenTelemetryMuleEventProcessor {


    private static Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleEventProcessor.class);

    // Store for all active transactions in flight.
    private static TransactionStore transactionStore = new TransactionStore();

    // What to invoke when Mule process step starts.
    public static void handleProcessorStartEvent(MessageProcessorNotification notification) {
        logger.debug("Handling processor start event");
//        my_test_span.setAttribute("mule_param","some_value");
//        my_test_span.addEvent("Processor Start");
        String docName = null;
        try {
            docName = getDocName(notification);
        } catch (Exception e) {
            // Suppress
        }
        if (docName != null)
            transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)).setAttribute("doc.name", docName));
        else
            transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)));
    }

    // What to invoke when Mule process step ends.
    public static void handleProcessorEndEvent(MessageProcessorNotification notification) {
        logger.debug("Handling end event");

        Span span = transactionStore.getSpan(getTransactionId(notification), getSpanId(notification));
        span.end();
    }


	// What to invoke when Mule flow starts execution.
	public static void handleFlowStartEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow start event");

		if (!transactionStore.isTransactionPresent(getTransactionId(notification))) {
            SpanBuilder spanBuilder = OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification));
            try {
                spanBuilder.setAttribute("flow.name", getDocName(notification));
            } catch (Exception e) {
                // Suppress
            }
            transactionStore.storeTransaction(getTransactionId(notification), spanBuilder.startSpan());
        } else {
		    String docName = null;
            try {
                docName = getDocName(notification);
            } catch (Exception e) {
                // Suppress
            }
            if (docName != null)
		        transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)).setAttribute("doc.name", docName));
            else
                transactionStore.addSpan(getTransactionId(notification), getSpanId(notification), OpenTelemetryStarter.getTracer().spanBuilder(getSpanName(notification)));
        }
	}

    private static String getDocName(PipelineMessageNotification notification) {
        return String.valueOf(((Map) notification.getInfo().getComponent().getAnnotation(QName.valueOf("{config}componentParameters"))).get("name"));
    }

    private static String getDocName(MessageProcessorNotification notification) throws NullPointerException, ClassCastException {
        return String.valueOf(((Map) notification.getInfo().getComponent().getAnnotation(QName.valueOf("{config}componentParameters"))).get("doc:name"));
    }

    // What to invoke when Mule flow completes execution.
	public static void handleFlowEndEvent(PipelineMessageNotification notification) {
		logger.debug("Handling flow end event");
		transactionStore.endTransaction(getTransactionId(notification));

    }

    public static String getSpanName(MessageProcessorNotification notification) {
        return notification.getComponent().getIdentifier().getName();
    }

    public static String getSpanName(PipelineMessageNotification notification) {
        String name = notification.getComponent().getIdentifier().getName();
        return name;
    }

    public static String getSpanId(MessageProcessorNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }

    public static String getSpanId(PipelineMessageNotification notification) {
        return notification.getInfo().getComponent().getLocation().getLocation();
    }


    private static String getTransactionId(MessageProcessorNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

    private static String getTransactionId(PipelineMessageNotification notification) {
        return notification.getEvent().getCorrelationId();
    }

}