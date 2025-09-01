package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.internal.processor.ComponentWrapper;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.FunctionMemoizer;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The ComponentRegistryService class provides management and initialization of
 * component wrappers for various components in a registry. It relies on the
 * ComponentLocatorService to locate components and their corresponding
 * locations.
 * This service supports lazy and eager initialization of component wrappers.
 */
public class ComponentRegistryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ComponentRegistryService.class);

  private final ComponentLocatorService componentLocatorService;
  private final ConcurrentHashMap<String, ComponentWrapper> componentWrapperRegistry = new ConcurrentHashMap<>();

  /**
   * Collect all otel specific system properties and cache them in a map.
   */
  public final Map<String, String> OTEL_SYSTEM_PROPERTIES_MAP = System.getProperties().stringPropertyNames()
      .stream()
      .filter(p -> p.contains(".otel.")).collect(Collectors.toMap(String::toLowerCase, System::getProperty));

  private final FunctionMemoizer<String, Map<String, String>> getGlobalConfigOtelSystemProperties = FunctionMemoizer
      .memoize(configName -> OpenTelemetryUtil.getGlobalConfigSystemAttributes(configName,
          OTEL_SYSTEM_PROPERTIES_MAP));

  @Inject
  public ComponentRegistryService(ConfigurationComponentLocator configurationComponentLocator) {
    this.componentLocatorService = new ComponentLocatorService(configurationComponentLocator);
  }

  public void initializeComponentWrapperRegistry() {
    componentWrapperRegistry.clear();
    Map<String, ComponentLocation> locations = this.componentLocatorService.getAllComponentLocations();
    for (String location : locations.keySet()) {
      LOGGER.trace("Finding component wrapper for {}", location);
      Component component = this.componentLocatorService.findComponentByLocation(location);
      if (component == null) {
        LOGGER.debug(
            "Component not found for location {}, it may have not been initialized. Later lookups will resolve it when needed.",
            location);
        continue;
      }
      try {
        LOGGER.trace("Initialization of component wrapper for {}", location);
        ComponentWrapper wrapper = new ComponentWrapper(component, this);
        if (wrapper.getConfigRef() != null) {
          // initialize the cache for cache system properties
          getGlobalConfigOtelSystemProperties.apply(wrapper.getConfigRef());
        }
        componentWrapperRegistry.put(component.getLocation().getLocation(), wrapper);
      } catch (Exception ex) {
        LOGGER.warn(
            "Could not pre-initialize component wrapper for {}. Processing will continue and wrapper may be initialized when needed.",
            location);
        LOGGER.trace("Exception during initialization of component wrapper for {}.", location, ex);
      }
    }
  }

  public Map<String, String> getOtelSystemPropertiesMap() {
    return OTEL_SYSTEM_PROPERTIES_MAP;
  }

  public Map<String, String> getGlobalConfigOtelSystemProperties(String configName) {
    return getGlobalConfigOtelSystemProperties.apply(configName);
  }

  public ComponentWrapper getComponentWrapper(String location) {
    return componentWrapperRegistry.computeIfAbsent(location,
        c -> {
          LOGGER.trace("Delayed Initialization of component wrapper for {}", location);
          return getComponentWrapper(componentLocatorService.findComponentByLocation(location));
        });
  }

  public ComponentWrapper getComponentWrapper(Component component) {
    return componentWrapperRegistry.computeIfAbsent(component.getLocation().getLocation(),
        c -> {
          LOGGER.trace("Delayed Initialization of component wrapper for {}",
              component.getLocation().getLocation());
          return new ComponentWrapper(component, this);
        });
  }

  public Map<String, ComponentLocation> getAllComponentLocations() {
    return componentLocatorService.getAllComponentLocations();
  }

  public ComponentLocation findComponentLocation(String location) {
    return componentLocatorService.findComponentLocation(location);
  }

  public long findSiblingCount(String location) {
    return componentLocatorService.findSiblingCount(location);
  }

  public Component findComponentByLocation(String location) {
    return componentLocatorService.findComponentByLocation(location);
  }

  public Component findComponentByLocation(ComponentLocation location) {
    return componentLocatorService.findComponentByLocation(location);
  }

}
