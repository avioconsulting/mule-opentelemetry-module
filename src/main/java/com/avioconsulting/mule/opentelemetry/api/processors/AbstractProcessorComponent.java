package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractProcessorComponent implements ProcessorComponent{

    static final String NAMESPACE_URI_MULE = "http://www.mulesoft.org/schema/mule/core";
    public static final String NAMESPACE_MULE = "mule";
    public static final String FLOW = "flow";

    protected <T> T getComponentAnnotation(String annotationName, EnrichedServerNotification notification){
        return (T) notification.getInfo().getComponent().getAnnotation(QName.valueOf(annotationName));
    }

    protected String getComponentParameterName(EnrichedServerNotification notification){
        Map<String, String> parameters = getComponentAnnotation("{config}componentParameters", notification);
        return parameters.get("name");
    }

    protected String getComponentDocName(EnrichedServerNotification notification){
        Map<String, String> parameters = getComponentAnnotation("{config}componentParameters", notification);
        return parameters.get("doc:name");
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
