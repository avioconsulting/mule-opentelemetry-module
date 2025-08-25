package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.internal.processor.ComponentWrapper;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ComponentWrapperService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ComponentWrapperService.class);

  private final ConfigurationComponentLocator configurationComponentLocator;

  private final ConcurrentHashMap<String, ComponentWrapper> componentWrapperRegistry = new ConcurrentHashMap<>();

  @Inject
  public ComponentWrapperService(ConfigurationComponentLocator configurationComponentLocator) {
    this.configurationComponentLocator = configurationComponentLocator;
  }

  public void initializeComponentWrapperRegistry() {
    System.out.println("####### ComponentWrapperService InitializeComponentWrapperRegistry called #######");
    componentWrapperRegistry.clear();
    List<ComponentLocation> locations = this.configurationComponentLocator.findAllLocations();
    for (ComponentLocation location : locations) {
      LOGGER.trace("Finding component wrapper for {}", location.getLocation());
      Optional<Component> componentOptional = this.configurationComponentLocator
          .find(Location.builderFromStringRepresentation(location.getLocation()).build());
      if (!componentOptional.isPresent()) {
        LOGGER.debug(
            "Component not found for location {}, it may have not been initialized. Later lookups will resolve it when needed.",
            location.getLocation());
        continue;
      }
      try {
        LOGGER.trace("Initialization of component wrapper for {}", location.getLocation());
        Component component = componentOptional.get();
        ComponentWrapper wrapper = new ComponentWrapper(component, configurationComponentLocator);
        componentWrapperRegistry.put(component.getLocation().getLocation(), wrapper);
      } catch (Exception ex) {
        LOGGER.warn(
            "Could not pre-initialize component wrapper for {}. Processing will continue and wrapper may be initialized when needed.",
            location.getLocation(), ex);
      }
    }
  }

  public ComponentWrapper getComponentWrapper(Component component) {
    return componentWrapperRegistry.computeIfAbsent(component.getLocation().getLocation(),
        c -> {
          LOGGER.trace("Delayed Initialization of component wrapper for {}",
              component.getLocation().getLocation());
          return new ComponentWrapper(component, configurationComponentLocator);
        });
  }
}
