package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.internal.util.BiFunctionMemoizer;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.FunctionMemoizer;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;

import java.util.*;

/**
 * Service for managing and locating components and their respective locations.
 * This service provides caching mechanisms for operations to optimize
 * performance.
 * It uses memoization for frequently accessed data to reduce redundant
 * operations.
 */
class ComponentLocatorService {

  private final Map<String, ComponentLocation> componentLocationMap;
  private final FunctionMemoizer<String, Component> findComponentByLocation;
  private final BiFunctionMemoizer<String, ComponentLocation, Component> findComponentByComponentLocation;
  private final FunctionMemoizer<String, Long> countSiblingsByLocation;
  private final ConfigurationComponentLocator configurationComponentLocator;

  ComponentLocatorService(ConfigurationComponentLocator configurationComponentLocator) {
    this.configurationComponentLocator = configurationComponentLocator;
    HashMap<String, ComponentLocation> map = new HashMap<>();
    configurationComponentLocator.findAllLocations()
        .forEach(l -> map.put(l.getLocation(), l));
    componentLocationMap = Collections.unmodifiableMap(map);

    findComponentByLocation = FunctionMemoizer.memoize(location -> {
      Location l = Location.builderFromStringRepresentation(location).build();
      return configurationComponentLocator.find(l).orElse(null);
    });

    findComponentByComponentLocation = BiFunctionMemoizer.memoize((key, location) -> {
      Location l = Location.builderFromStringRepresentation(location.getLocation()).build();
      return configurationComponentLocator.find(l).orElseGet(() -> this.configurationComponentLocator.find(
          location.getComponentIdentifier().getIdentifier())
          .stream()
          .filter(c -> c.getLocation().getLocation().equalsIgnoreCase(location.getLocation())).findFirst()
          .orElse(null));
    });
    countSiblingsByLocation = FunctionMemoizer.memoize(location -> componentLocationMap.keySet().stream()
        .filter(l -> l.equalsIgnoreCase(ComponentsUtil.getLocationParent(location) + "/"))
        .count());
  }

  public Map<String, ComponentLocation> getAllComponentLocations() {
    return componentLocationMap;
  }

  public ComponentLocation findComponentLocation(String location) {
    return componentLocationMap.get(location);
  }

  public long findSiblingCount(String location) {
    return countSiblingsByLocation.apply(location);
  }

  public Component findComponentByLocation(String location) {
    return this.findComponentByLocation.apply(location);
  }

  public Component findComponentByLocation(ComponentLocation location) {
    return this.findComponentByComponentLocation.apply(location.getLocation(), location);
  }

}
