package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractProcessorComponent implements ProcessorComponent{

    static final String NAMESPACE_URI_MULE = "http://www.mulesoft.org/schema/mule/core";
    public static final String NAMESPACE_MULE = "mule";
    public static final String FLOW = "flow";

    @Override
    public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
        return TraceComponent.newBuilder(notification.getResourceIdentifier())
                .withTransactionId(getTransactionId(notification))
                .withLocation(notification.getComponent().getLocation().getLocation())
                .withErrorMessage(notification.getEvent().getError().map(Error::getDescription).orElse(null))
                .build();
    }

    protected TraceComponent.Builder getBaseTraceComponent(EnrichedServerNotification notification) {
        return TraceComponent
                .newBuilder(notification.getComponent().getLocation().getLocation())
                .withLocation(notification.getComponent().getLocation().getLocation())
                .withSpanName(notification.getComponent().getIdentifier().getName())
                .withTransactionId(getTransactionId(notification));
    }
    protected String getTransactionId(EnrichedServerNotification notification) {
        return notification.getEvent().getCorrelationId();
    }
    protected <T> T getComponentAnnotation(String annotationName, EnrichedServerNotification notification){
        return (T) notification.getInfo().getComponent().getAnnotation(QName.valueOf(annotationName));
    }

    protected String getComponentParameterName(EnrichedServerNotification notification){
        return getComponentParameter(notification, "name");
    }
    protected String getComponentParameter(EnrichedServerNotification notification, String parameter){
        return getComponentParameters(notification).get(parameter);
    }
    private Map<String, String> getComponentParameters(EnrichedServerNotification notification) {
        return getComponentAnnotation("{config}componentParameters", notification);
    }
    protected String getComponentConfigRef(EnrichedServerNotification notification){
        return getComponentParameter(notification, "config-ref");
    }
    protected String getComponentDocName(EnrichedServerNotification notification){
        return getComponentParameter(notification, "doc:name");
    }

    protected Map<String, String> getProcessorCommonTags(EnrichedServerNotification notification){
        String docName = getComponentDocName(notification);
        Map<String, String> tags = new HashMap<>();
        tags.put("mule.processor.namespace", notification.getComponent().getIdentifier().getNamespace());
        tags.put("mule.processor.name", notification.getComponent().getIdentifier().getName());
        if(docName != null) tags.put("mule.processor.docName", docName);
        return tags;
    }

    protected ComponentIdentifier getSourceIdentifier(EnrichedServerNotification notification) {
        ComponentIdentifier sourceIdentifier = null;
        if (notification.getEvent() != null && notification.getEvent().getContext().getOriginatingLocation() != null) {
            sourceIdentifier = notification.getEvent().getContext().getOriginatingLocation().getComponentIdentifier().getIdentifier();
        }
        return sourceIdentifier;
    }
}
