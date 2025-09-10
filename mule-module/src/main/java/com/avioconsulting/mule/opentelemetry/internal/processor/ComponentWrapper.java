package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF;

public class ComponentWrapper {

  public static final String COMPONENT_NAMESPACE_KEY = "component:namespace";
  public static final String COMPONENT_NAME_KEY = "component:name";
  private final Component component;
  private final Map<String, String> parameters;
  private final Map<String, String> configParameters;
  private final Map<String, String> connectionParameters;
  private final ComponentRegistryService componentRegistryService;
  private String defaultSpanName;
  private static final Logger LOGGER = LoggerFactory.getLogger(ComponentWrapper.class);
  private final Map<String, String> staticParameters;

  public ComponentWrapper(Component component, ComponentRegistryService componentRegistryService) {
    this.component = component;
    this.componentRegistryService = componentRegistryService;
    Map<? extends String, ? extends String> componentAnnotation = getComponentAnnotation(
        "{config}componentParameters");
    parameters = componentAnnotation != null ? Collections.unmodifiableMap(componentAnnotation)
        : Collections.emptyMap();
    configParameters = Collections.unmodifiableMap(initConfigMap());
    connectionParameters = Collections.unmodifiableMap(initConnectionParameters());
    setDefaultSpanName(component);
    staticParameters = setStaticParameters();
  }

  private Map<String, String> setStaticParameters() {
    Map<String, String> staticParameters = new HashMap<>();
    staticParameters.put(MULE_APP_PROCESSOR_NAMESPACE.getKey(),
        component.getIdentifier().getNamespace());
    staticParameters.put(MULE_APP_PROCESSOR_NAME.getKey(), component.getIdentifier().getName());
    if (this.getDocName() != null)
      staticParameters.put(MULE_APP_PROCESSOR_DOC_NAME.getKey(), this.getDocName());
    if (this.getConfigRef() != null) {
      staticParameters.put(MULE_APP_PROCESSOR_CONFIG_REF.getKey(), this.getConfigRef());
      staticParameters.putAll(componentRegistryService.getGlobalConfigOtelSystemProperties(this.getConfigRef()));
    }
    return Collections.unmodifiableMap(staticParameters);
  }

  /**
   * Retrieves the static parameters associated with the component.
   *
   * @return an unmodifiable {@link Map} of static parameters where the keys and
   *         values are both {@link String}.
   */
  public Map<String, String> staticParametersAsReadOnlyMap() {
    return staticParameters;
  }

  private void setDefaultSpanName(Component component) {
    String name = component.getLocation().getComponentIdentifier().getIdentifier().getNamespace();
    if (name.equalsIgnoreCase("apikit")) {
      defaultSpanName = component.getIdentifier().getName() + ":" +
          (getDocName() == null ? component.getIdentifier().getName() : getDocName())
          + " " + getConfigRef();
    } else if (name.equalsIgnoreCase("anypoint-mq")) {
      defaultSpanName = getParameter("destination") + " " + "publish";
    } else if (name.equalsIgnoreCase("wsc")) {
      defaultSpanName = getConfigConnectionParameters().get("service") + ":" + getParameter("operation");
    } else {
      defaultSpanName = component.getIdentifier().getName() + ":" +
          (getDocName() == null ? component.getIdentifier().getName() : getDocName());
    }
  }

  public String getDefaultSpanName() {
    return defaultSpanName;
  }

  public Component getComponent() {
    return component;
  }

  private <T> T getComponentAnnotation(
      String annotationName) {
    return (T) component.getAnnotation(QName.valueOf(annotationName));
  }

  /**
   * Get Component Parameters (Unmodifiable) Map
   * 
   * @return {@link Map}
   */
  public Map<String, String> getParameters() {
    return parameters;
  }

  public String getName() {
    return parameters.get("name");
  }

  public String getParameter(String parameter) {
    return parameters.get(parameter);
  }

  public String getConfigRef() {
    return parameters.get("config-ref");
  }

  public String getDocName() {
    return parameters.get("doc:name");
  }

  public Map<String, String> getConfigConnectionParameters() {
    return connectionParameters;
  }

  private Map<String, String> initConnectionParameters() {
    String componentConfigRef = getConfigRef();
    if (componentConfigRef == null)
      return Collections.emptyMap();
    try {
      Component component = componentRegistryService.findComponentByLocation(
          Location.builder().globalName(componentConfigRef).addConnectionPart().build().toString());
      if (component == null) {
        return Collections.emptyMap();
      }
      return toExtendedParameters(new ComponentWrapper(component, componentRegistryService));
    } catch (Exception ex) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Failed to extract connection parameters for {}. Ignoring this failure - {}",
            componentConfigRef,
            ex.getMessage());
      }
      return Collections.emptyMap();
    }

  }

  public Map<String, String> getConfigParameters() {
    return configParameters;
  }

  private Map<String, String> initConfigMap() {
    String componentConfigRef = getConfigRef();
    if (componentConfigRef == null)
      return Collections.emptyMap();
    try {
      Component component = componentRegistryService
          .findComponentByLocation(Location.builder().globalName(componentConfigRef).build().toString());
      if (component == null) {
        return Collections.emptyMap();
      }
      return toExtendedParameters(new ComponentWrapper(component, componentRegistryService));
    } catch (Exception ex) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Failed to extract connection parameters for {}. Ignoring this failure - {}",
            componentConfigRef,
            ex.getMessage());
      }
      return Collections.emptyMap();
    }
  }

  private Map<String, String> toExtendedParameters(ComponentWrapper componentWrapper) {
    Map<String, String> map = new HashMap<>(componentWrapper.getParameters());
    map.put(COMPONENT_NAMESPACE_KEY, componentWrapper.getComponent().getIdentifier().getNamespace());
    map.put(COMPONENT_NAME_KEY, componentWrapper.getComponent().getIdentifier().getName());
    return map;
  }

  @Override
  public String toString() {
    return "ComponentWrapper{" +
        "parameters=" + parameters +
        ", configParameters=" + configParameters +
        ", connectionParameters=" + connectionParameters +
        ", defaultSpanName='" + defaultSpanName + '\'' +
        ", staticParameters=" + staticParameters +
        ", component=" + component +
        '}';
  }
}
