package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.LocationPart;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.copyBatchTags;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.FLOW;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ROUTE;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.SCOPE;

public class ComponentsUtil {

  public static final String BATCH_AGGREGATOR = "batch:aggregator";
  private static final List<TypedComponentIdentifier.ComponentType> ROUTE_IDENTIFIERS = Arrays.asList(ROUTE, SCOPE);
  public static final String BATCH_STEP_TAG = "batch:step";
  public static final String BATCH_STEP_RECORD_TAG = "batch:step-record";
  public static final String BATCH_JOB_TAG = "batch:job";
  public static final String BATCH_ON_COMPLETE_TAG = "batch:on-complete";
  public static final String PROCESSOR_0_PATH_SUFFIX = "/processors/0";
  public static final String ASYNC = "async";
  public static final String ROUTE_NAME = "route";
  public static final String SUB_FLOW = "sub-flow";
  public static final String FLOW_REF = "flow-ref";
  public static final String BATCH = "batch";
  public static final String ON_COMPLETE = "on-complete";
  public static final String MULE_ANY = "MULE:ANY";

  public static Optional<ComponentLocation> findLocation(String location,
      ConfigurationComponentLocator configurationComponentLocator) {
    return configurationComponentLocator.findAllLocations().stream().filter(cl -> cl.getLocation().equals(location))
        .findFirst();
  }

  public static boolean isSubFlow(ComponentLocation location) {
    return location.getComponentIdentifier().getIdentifier().getName().equals(SUB_FLOW);
  }

  public static boolean isFlowRef(ComponentLocation location) {
    return location.getComponentIdentifier().getIdentifier().getName().equals(FLOW_REF);
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
    return tci.getIdentifier().getName().equals(ROUTE_NAME)
        || ROUTE.equals(tci.getType());
  }

  public static boolean isFlowTrace(TraceComponent traceComponent) {
    return traceComponent != null && traceComponent.getTags() != null
        && "flow".equalsIgnoreCase(traceComponent.getTags().get(MULE_APP_PROCESSOR_NAME.getKey()));
  }

  public static boolean isFirstProcessor(ComponentLocation location) {
    String interceptPath = location.getRootContainerName() + PROCESSOR_0_PATH_SUFFIX;
    return isFlowTypeContainer(location)
        && interceptPath.equalsIgnoreCase(location.getLocation());
  }

  public static boolean isFirstProcessorInScope(ComponentLocation location) {
    if (location.getParts().size() <= 2)
      return false;
    LocationPart parentPart = location.getParts().get(location.getParts().size() - 3);
    String firstProcessorPath = getLocationParent(location.getLocation()) + PROCESSOR_0_PATH_SUFFIX;
    return parentPart.getPartIdentifier().isPresent() && isRoute(parentPart.getPartIdentifier().get())
        && firstProcessorPath.equalsIgnoreCase(location.getLocation());
  }

  public static boolean isFlowTypeContainer(ComponentLocation componentLocation) {
    return !componentLocation.getParts().isEmpty() && componentLocation.getParts().get(0).getPartIdentifier()
        .filter(c -> FLOW.equals(c.getType())
            || (SCOPE.equals(c.getType())))
        .isPresent();
  }

  public static boolean isAsyncScope(TypedComponentIdentifier identifier) {
    return SCOPE.equals(identifier.getType()) && identifier.getIdentifier().getName().equals(ASYNC);
  }

  /**
   * Build a Trace component for sub-flow
   * 
   * @param subFlowComp
   *            {@link ComponentLocation} of the target sub-flow
   * @param traceComponent
   *            of the flow-ref invoking the sub-flow
   * @return {@link TraceComponent} for the sub-flow
   */
  public static TraceComponent getSubFlowTraceComponent(ComponentLocation subFlowComp,
      TraceComponent traceComponent) {
    TraceComponent subFlowTrace = TraceComponent.of(subFlowComp)
        .withTransactionId(traceComponent.getTransactionId())
        .withSpanName(subFlowComp.getLocation())
        .withSpanKind(SpanKind.INTERNAL)
        .withTags(new HashMap<>())
        .withStatsCode(traceComponent.getStatusCode())
        .withStartTime(traceComponent.getStartTime())
        .withContext(traceComponent.getContext())
        .withEventContextId(traceComponent.getEventContextId());
    subFlowTrace.getTags().put(MULE_APP_SCOPE_SUBFLOW_NAME.getKey(),
        subFlowComp.getLocation());
    copyBatchTags(traceComponent, subFlowTrace);
    return subFlowTrace;
  }

  /**
   * Resolves the target flow name using given #expressionManager and updates it
   * in {@link TraceComponent#getTags()}.
   * Then it looks up the component location for the resolved flow using given
   * {@link ComponentRegistryService}.
   * 
   * @param expressionManager
   *            {@link ExpressionManager} to resolve names
   * @param traceComponent
   *            {@link TraceComponent} of the flow-ref
   * @param context
   *            {@link BindingContext} to use with {@link ExpressionManager}
   * @param componentRegistryService
   *            {@link ComponentRegistryService} to look up components
   * @return ComponentLocation of resolved target flow
   */
  public static ComponentLocation resolveFlowName(ExpressionManager expressionManager,
      TraceComponent traceComponent, BindingContext context,
      ComponentRegistryService componentRegistryService) {
    String targetFlowName = traceComponent.getTags().get(MULE_APP_PROCESSOR_FLOW_REF_NAME.getKey());
    if (expressionManager
        .isExpression(targetFlowName)) {
      targetFlowName = expressionManager
          .evaluate(targetFlowName, context).getValue().toString();
      traceComponent.getTags().put(MULE_APP_PROCESSOR_FLOW_REF_NAME.getKey(), targetFlowName);
    }
    ComponentLocation componentLocation = componentRegistryService.findComponentLocation(targetFlowName);
    if (componentLocation == null) {
      return null;
    } else if (isSubFlow(componentLocation)) {
      return componentLocation;
    }
    return null;
  }

  public static boolean isBatchOnComplete(String location, ComponentRegistryService componentRegistryService) {
    Component component = componentRegistryService.findComponentByLocation(location);
    return component != null && BATCH.equalsIgnoreCase(component.getIdentifier().getNamespace())
        && ON_COMPLETE.equalsIgnoreCase(component.getIdentifier().getName());
  }
}
