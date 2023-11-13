package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;

import java.util.*;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

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

  protected abstract String getNamespace();

  protected abstract List<String> getOperations();

  protected abstract List<String> getSources();

  protected SpanKind getSpanKind() {
    return SpanKind.INTERNAL;
  }

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return getNamespace().equalsIgnoreCase(componentIdentifier.getNamespace())
        && (getOperations().contains(componentIdentifier.getName().toLowerCase())
            || getSources().contains(componentIdentifier.getName().toLowerCase()));
  }

  protected boolean namespaceSupported(ComponentIdentifier componentIdentifier) {
    return getNamespace().equalsIgnoreCase(componentIdentifier.getNamespace().toLowerCase());
  }

  protected boolean operationSupported(ComponentIdentifier componentIdentifier) {
    return getOperations().contains(componentIdentifier.getName().toLowerCase());
  }

  protected boolean sourceSupported(ComponentIdentifier componentIdentifier) {
    return getSources().contains(componentIdentifier.getName().toLowerCase());
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    return getTraceComponentBuilderFor(notification)
        .build();
  }

  protected TraceComponent.Builder getTraceComponentBuilderFor(EnrichedServerNotification notification) {
    return TraceComponent.newBuilder(notification.getResourceIdentifier())
        .withTransactionId(getTransactionId(notification))
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withTags(new HashMap<>())
        .withErrorMessage(
            notification.getEvent().getError().map(Error::getDescription).orElse(null));
  }

  protected TraceComponent.Builder getBaseTraceComponent(
      EnrichedServerNotification notification) {
    return TraceComponent.newBuilder(notification.getComponent().getLocation().getLocation())
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withSpanName(notification.getComponent().getIdentifier().getName())
        .withTransactionId(getTransactionId(notification));
  }

  protected String getDefaultSpanName(Map<String, String> tags) {
    String name = tags.get(MULE_APP_PROCESSOR_NAME.getKey());
    return name.concat(":")
        .concat(tags.getOrDefault(MULE_APP_PROCESSOR_DOC_NAME.getKey(), name));
  }

  protected String getTransactionId(EnrichedServerNotification notification) {
    return notification.getEvent().getCorrelationId();
  }

  protected Map<String, String> getProcessorCommonTags(Component component) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component,
        configurationComponentLocator);
    Map<String, String> tags = new HashMap<>();
    tags.put(MULE_APP_PROCESSOR_NAMESPACE.getKey(),
        component.getIdentifier().getNamespace());
    tags.put(MULE_APP_PROCESSOR_NAME.getKey(), component.getIdentifier().getName());
    if (componentWrapper.getDocName() != null)
      tags.put(MULE_APP_PROCESSOR_DOC_NAME.getKey(), componentWrapper.getDocName());
    if (componentWrapper.getConfigRef() != null)
      tags.put(MULE_APP_PROCESSOR_CONFIG_REF.getKey(), componentWrapper.getConfigRef());
    return tags;
  }

  protected ComponentIdentifier getSourceIdentifier(EnrichedServerNotification notification) {
    ComponentIdentifier sourceIdentifier = null;
    if (notification.getEvent() != null
        && notification.getEvent().getContext().getOriginatingLocation() != null
        && notification.getResourceIdentifier().equalsIgnoreCase(
            notification.getEvent().getContext().getOriginatingLocation().getRootContainerName())) {
      sourceIdentifier = notification
          .getEvent()
          .getContext()
          .getOriginatingLocation()
          .getComponentIdentifier()
          .getIdentifier();
    }
    return sourceIdentifier;
  }

  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    return Collections.emptyMap();
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    return getStartTraceComponent(notification.getComponent(), notification.getEvent().getMessage(),
        getTransactionId(notification));
  }

  /**
   * Create a start trace component without the notification object. This is
   * mostly consumed by interceptors.
   * 
   * @param component
   *            {@link Component}
   * @param message
   *            {@link Message}
   * @param correlationId
   *            {@link String}
   * @return TraceComponent
   */
  public TraceComponent getStartTraceComponent(Component component, Message message, String correlationId) {
    Map<String, String> tags = new HashMap<>(getProcessorCommonTags(component));
    tags.put(MULE_CORRELATION_ID.getKey(), correlationId);
    tags.putAll(getAttributes(component,
        message.getAttributes()));
    return TraceComponent.newBuilder(component.getLocation().getLocation())
        .withLocation(component.getLocation().getLocation())
        .withSpanName(getDefaultSpanName(tags))
        .withTags(tags)
        .withSpanKind(getSpanKind())
        .withTransactionId(correlationId)
        .build();
  }

  protected void addTagIfPresent(Map<String, String> sourceMap, String sourceKey, Map<String, String> targetMap,
      String targetKey) {
    if (sourceMap.containsKey(sourceKey))
      targetMap.put(targetKey, sourceMap.get(sourceKey));
  }

  protected Optional<Component> getSourceComponent(EnrichedServerNotification notification) {
    Optional<Component> component = configurationComponentLocator.find(Location.builderFromStringRepresentation(
        notification.getEvent().getContext().getOriginatingLocation().getLocation()).build());
    return component;
  }

  protected enum ContextMapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Nullable
    @Override
    public String get(@Nullable Map<String, String> map, String s) {
      return map == null ? null : map.get(s);
    }
  }
}
