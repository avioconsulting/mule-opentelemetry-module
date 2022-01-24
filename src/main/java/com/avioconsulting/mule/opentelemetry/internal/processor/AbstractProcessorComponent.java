package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;

import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.notification.EnrichedServerNotification;

public abstract class AbstractProcessorComponent implements ProcessorComponent {

  static final String NAMESPACE_URI_MULE = "http://www.mulesoft.org/schema/mule/core";
  public static final String NAMESPACE_MULE = "mule";
  public static final String FLOW = "flow";

  protected ConfigurationComponentLocator configurationComponentLocator;

  @Override
  public ProcessorComponent withConfigurationComponentLocator(
      ConfigurationComponentLocator configurationComponentLocator) {
    this.configurationComponentLocator = configurationComponentLocator;
    return this;
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTransactionId(getTransactionId(notification))
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withErrorMessage(
            notification.getEvent().getError().map(Error::getDescription).orElse(null))
        .build();
  }

  protected TraceComponent.Builder getBaseTraceComponent(
      EnrichedServerNotification notification) {
    return TraceComponent.newBuilder(notification.getComponent().getLocation().getLocation())
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withSpanName(notification.getComponent().getIdentifier().getName())
        .withTransactionId(getTransactionId(notification));
  }

  protected String getTransactionId(EnrichedServerNotification notification) {
    return notification.getEvent().getCorrelationId();
  }

  protected <T> T getComponentAnnotation(
      String annotationName, EnrichedServerNotification notification) {
    return getComponentAnnotation(annotationName, notification.getInfo().getComponent());
  }

  protected <T> T getComponentAnnotation(
      String annotationName, Component component) {
    return (T) component.getAnnotation(QName.valueOf(annotationName));
  }

  protected String getComponentParameterName(EnrichedServerNotification notification) {
    return getComponentParameter(notification, "name");
  }

  protected String getComponentParameter(
      EnrichedServerNotification notification, String parameter) {
    return getComponentParameters(notification).get(parameter);
  }

  private Map<String, String> getComponentParameters(EnrichedServerNotification notification) {
    return getComponentAnnotation("{config}componentParameters", notification);
  }

  private Map<String, String> getComponentParameters(Component component) {
    return getComponentAnnotation("{config}componentParameters", component);
  }

  protected String getComponentConfigRef(EnrichedServerNotification notification) {
    return getComponentParameter(notification, "config-ref");
  }

  protected Map<String, String> getConfigConnectionParameters(EnrichedServerNotification notification) {
    try {
      String componentConfigRef = getComponentConfigRef(notification);
      return configurationComponentLocator
          .find(Location.builder().globalName(componentConfigRef).addConnectionPart().build())
          .map(this::getComponentParameters).orElse(Collections.emptyMap());
    } catch (Exception ex) {
      return Collections.emptyMap();
    }

  }

  protected String getComponentDocName(EnrichedServerNotification notification) {
    return getComponentParameter(notification, "doc:name");
  }

  protected Map<String, String> getProcessorCommonTags(EnrichedServerNotification notification) {
    String docName = getComponentDocName(notification);
    Map<String, String> tags = new HashMap<>();
    tags.put(
        "mule.processor.namespace",
        notification.getComponent().getIdentifier().getNamespace());
    tags.put("mule.processor.name", notification.getComponent().getIdentifier().getName());
    if (docName != null)
      tags.put("mule.processor.docName", docName);
    return tags;
  }

  protected ComponentIdentifier getSourceIdentifier(EnrichedServerNotification notification) {
    ComponentIdentifier sourceIdentifier = null;
    if (notification.getEvent() != null
        && notification.getEvent().getContext().getOriginatingLocation() != null) {
      sourceIdentifier = notification
          .getEvent()
          .getContext()
          .getOriginatingLocation()
          .getComponentIdentifier()
          .getIdentifier();
    }
    return sourceIdentifier;
  }
}
