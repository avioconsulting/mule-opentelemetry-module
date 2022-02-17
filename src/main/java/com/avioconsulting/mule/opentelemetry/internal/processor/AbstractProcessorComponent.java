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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.SemanticAttributes.*;

public abstract class AbstractProcessorComponent implements ProcessorComponent {

  static final String NAMESPACE_URI_MULE = "http://www.mulesoft.org/schema/mule/core";
  public static final String NAMESPACE_MULE = "mule";
  public static final String FLOW = "flow";

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessorComponent.class);

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

  protected String getDefaultSpanName(EnrichedServerNotification notification) {
    return notification.getComponent().getIdentifier().getName().concat(":")
        .concat(getComponentDocName(notification));
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
    String componentConfigRef = getComponentConfigRef(notification);
    try {
      return configurationComponentLocator
          .find(Location.builder().globalName(componentConfigRef).addConnectionPart().build())
          .map(this::getComponentParameters).orElse(Collections.emptyMap());
    } catch (Exception ex) {
      LOGGER.trace(
          "Failed to extract connection parameters for {}. Ignoring this failure - {}", componentConfigRef,
          ex.getMessage());
      return Collections.emptyMap();
    }

  }

  protected Map<String, String> getConfigParameters(EnrichedServerNotification notification) {
    String componentConfigRef = getComponentConfigRef(notification);
    try {
      return configurationComponentLocator
          .find(Location.builder().globalName(componentConfigRef).build())
          .map(this::getComponentParameters).orElse(Collections.emptyMap());
    } catch (Exception ex) {
      LOGGER.trace(
          "Failed to extract connection parameters for {}. Ignoring this failure - {}", componentConfigRef,
          ex.getMessage());
      return Collections.emptyMap();
    }

  }

  protected String getComponentDocName(EnrichedServerNotification notification) {
    return getComponentParameter(notification, "doc:name");
  }

  protected Map<String, String> getProcessorCommonTags(EnrichedServerNotification notification) {
    String docName = getComponentDocName(notification);
    Map<String, String> tags = new HashMap<>();
    tags.put(MULE_APP_PROCESSOR_NAMESPACE.getKey(),
        notification.getComponent().getIdentifier().getNamespace());
    tags.put(MULE_APP_PROCESSOR_NAME.getKey(), notification.getComponent().getIdentifier().getName());
    if (docName != null)
      tags.put(MULE_APP_PROCESSOR_DOC_NAME.getKey(), docName);
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
