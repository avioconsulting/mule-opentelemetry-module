package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;

import java.util.*;
import java.util.function.Function;

import com.avioconsulting.mule.opentelemetry.internal.cache.InMemoryCache;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.message.Error;
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

  /**
   * Memory cache for Component's static tags.
   */
  protected final InMemoryCache<String, Map<String, String>> componentTagsCache = new InMemoryCache<>();

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

  protected Map<String, String> getProcessorCommonTags(EnrichedServerNotification notification) {

    Function<Component, Map<String, String>> extractTags = (component) -> {
      ComponentWrapper componentWrapper = new ComponentWrapper(notification.getInfo().getComponent(),
          configurationComponentLocator);
      Map<String, String> tags = new HashMap<>();
      tags.put(MULE_APP_PROCESSOR_NAMESPACE.getKey(),
          notification.getComponent().getIdentifier().getNamespace());
      tags.put(MULE_APP_PROCESSOR_NAME.getKey(), notification.getComponent().getIdentifier().getName());
      if (componentWrapper.getDocName() != null)
        tags.put(MULE_APP_PROCESSOR_DOC_NAME.getKey(), componentWrapper.getDocName());
      if (componentWrapper.getConfigRef() != null)
        tags.put(MULE_APP_PROCESSOR_CONFIG_REF.getKey(), componentWrapper.getConfigRef());
      return tags;
    };
    // Using cache key prefix to avoid conflict with any component tags for same
    // component.
    // We could also use a different instance of an InMemoryCache but prefix is good
    // enough for this.
    return componentTagsCache.cached(
        "common|".concat(notification.getInfo().getComponent().getLocation().getLocation()),
        (key) -> extractTags.apply(notification.getInfo().getComponent()));
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

  /**
   * <pre>
   * Collects the tags from component configuration using {@link #componentAttributesToTags(Component)} and current request attributes using {@link #requestAttributesToTags(TypedValue)}.
   *
   * The default implementation of this method uses a caching for component attributes as they are design time static values.
   * Provided {@link Component#getLocation()} 's {@link ComponentLocation#getLocation()} is used a cache key.
   *
   * The current {@link #requestAttributesToTags(TypedValue)} are then merged with the component tags.
   *
   * </pre>
   * 
   * @param component
   *            {@link Component} to extract tags for
   * @param attributes
   *            {@link TypedValue} of request attributes
   * @return Map
   * @param <A>
   *            Attribute class
   */
  protected <A> Map<String, String> getTagsFor(Component component, TypedValue<A> attributes) {
    Map<String, String> tags = new HashMap<>(componentTagsCache.cached(component.getLocation().getLocation(),
        (key) -> componentAttributesToTags(component)));
    Map<String, String> attributesToTags = requestAttributesToTags(attributes);
    if (attributesToTags != null) {
      tags.putAll(attributesToTags);
    }
    return tags;
  }

  /**
   * Convert request attributes to tags. As it is extracted from request
   * attributes, These are dynamic tags specific to current request and must not
   * be cached.
   * 
   * @param attributes
   *            {@link TypedValue} of A.
   * @return Map
   * @param <A>
   */
  protected <A> Map<String, String> requestAttributesToTags(TypedValue<A> attributes) {
    return new HashMap<>();
  }

  /**
   * Extract the static attributes from component definition. Implementations must
   * be pure i.e. for same instance of a {@link Component}, it must always return
   * the same response.
   * 
   * @param component
   *            {@link Component} to extract tags from
   * @return {@link Map }
   */
  protected Map<String, String> componentAttributesToTags(Component component) {
    return new HashMap<>();
  }

  @Override
  public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
    Map<String, String> tags = new HashMap<>(getProcessorCommonTags(notification));
    tags.put(MULE_CORRELATION_ID.getKey(), notification.getEvent().getCorrelationId());
    tags.putAll(getTagsFor(notification.getInfo().getComponent(),
        notification.getEvent().getMessage().getAttributes()));
    return TraceComponent.newBuilder(notification.getComponent().getLocation().getLocation())
        .withLocation(notification.getComponent().getLocation().getLocation())
        .withSpanName(getDefaultSpanName(tags))
        .withTags(tags)
        .withSpanKind(getSpanKind())
        .withTransactionId(getTransactionId(notification))
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
