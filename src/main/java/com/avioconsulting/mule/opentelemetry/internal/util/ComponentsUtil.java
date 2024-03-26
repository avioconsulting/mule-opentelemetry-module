package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.LocationPart;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ROUTE;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.SCOPE;
import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_PROCESSOR_NAME;

public class ComponentsUtil {

  private static List<TypedComponentIdentifier.ComponentType> ROUTE_IDENTIFIERS = Arrays.asList(ROUTE, SCOPE);

  public static Optional<ComponentLocation> findLocation(String location,
      ConfigurationComponentLocator configurationComponentLocator) {
    return configurationComponentLocator.findAllLocations().stream().filter(cl -> cl.getLocation().equals(location))
        .findFirst();
  }

  public static boolean isSubFlow(ComponentLocation location) {
    return location.getComponentIdentifier().getIdentifier().getName().equals("sub-flow");
  }

  public static boolean isFlowRef(ComponentLocation location) {
    return location.getComponentIdentifier().getIdentifier().getName().equals("flow-ref");
  }

  public static Optional<Component> findComponent(ComponentIdentifier identifier, String location,
      ConfigurationComponentLocator configurationComponentLocator) {
    return configurationComponentLocator
        .find(identifier).stream()
        .filter(c -> c.getLocation().getLocation().equals(location)).findFirst();
  }

  /**
   * Gets the parent container for router's route.
   * 
   * <br/>
   * <br/>
   *
   * For example, `get-greeting-2/processors/0/route/0/processors/0` location
   * represents following compoenets -
   *
   * <ul>
   * <li>get-greeting-2/processors/0/route/0/processors/0 - target processor</li>
   * <li>get-greeting-2/processors/0/route/0 - route in the router such as
   * Scatter-Gather route</li>
   * <li>get-greeting-2/processors/0 - router component as Scatter-Gather i.e
   * Parent container returned by this method</li>
   * </ul>
   * 
   * @param traceComponent
   *            {@link TraceComponent}
   * @return String
   */
  public static String getRouteContainerLocation(TraceComponent traceComponent) {
    String parentLocation = null;
    if (traceComponent.getComponentLocation() != null) {
      List<LocationPart> parts = traceComponent.getComponentLocation().getParts();
      if (parts.size() > 2) {
        int routeIndex = parts.size() - 3;
        LocationPart parentPart = parts.get(routeIndex);
        parentLocation = parentPart
            .getPartIdentifier()
            .filter(ComponentsUtil::isRoute)
            .map(tci -> {
              StringBuffer sb = new StringBuffer(parts.get(0).getPartPath());
              for (int i = 1; i <= routeIndex; i++) {
                sb.append("/")
                    .append(parts.get(i).getPartPath());
              }
              return sb.toString();
            }).orElse(null);
      }
    }
    return parentLocation;
  }

  /**
   * Find a parent location, usually represented 2 level up, for given location
   * string.
   * For example, `get-greeting-2/processors/0/route/0/processors/0` location
   * represents following compoenets -
   *
   * <ul>
   * <li>get-greeting-2/processors/0/processors/0 - target processor</li>
   * <li>get-greeting-2/processors/0 - Parent container returned by this
   * method</li>
   * </ul>
   *
   * @param location
   *            String
   * @return String
   */
  public static String getLocationParent(String location) {
    String locationParent = location;
    if (locationParent.contains("/"))
      locationParent = location.substring(0, location.lastIndexOf("/"));
    if (locationParent.contains("/"))
      locationParent = locationParent.substring(0, locationParent.lastIndexOf("/"));
    return locationParent;
  }

  public static boolean isRoute(TypedComponentIdentifier tci) {
    Objects.requireNonNull(tci, "Component Identifier cannot be null");
    return tci.getIdentifier().getName().equals("route");
  }

  public static boolean isFlowTrace(TraceComponent traceComponent) {
    return traceComponent != null && traceComponent.getTags() != null
        && "flow".equalsIgnoreCase(traceComponent.getTags().get(MULE_APP_PROCESSOR_NAME.getKey()));
  }
}
