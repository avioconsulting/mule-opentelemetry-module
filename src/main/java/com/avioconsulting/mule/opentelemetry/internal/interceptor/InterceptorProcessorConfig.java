package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleCoreProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.core.api.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration class for managing the interception of components based on
 * global and local configurations.
 * InterceptorProcessorConfig provides methods to set tracing configurations,
 * update trace configurations,
 * and determine if a specific component is enabled for interception.
 *
 * Interceptors can be completely turned off by setting a system property
 * mule.otel.interceptor.processor.enable=false.
 *
 * Interceptors can be restricted to the first processor in the container (eg.
 * Flow) by setting system property
 * mule.otel.interceptor.first.processor.only=true.
 *
 * @since 2.8
 */
public class InterceptorProcessorConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterceptorProcessorConfig.class);

  /**
   * Disable interceptor feature by setting this system property to `false`.
   * Default `true`.
   */
  public static final String MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME = "mule.otel.interceptor.processor.enable";
  private final boolean INTERCEPTOR_ENABLED_BY_SYS_PROPERTY = PropertiesUtil
      .getBoolean(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, true);

  /**
   * Enable interceptor feature for first processor in the container (eg. Flow) by
   * setting this system property to `true`. Default `false`.
   */
  public static final String MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY = "mule.otel.interceptor.first.processor.only";
  // Negating the property value since usage is negated
  private final boolean NOT_FIRST_PROCESSOR_ONLY_MODE = !PropertiesUtil
      .getBoolean(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY, false);

  private boolean turnOffTracing = false;

  /**
   * Components that must be intercepted
   */
  private final List<String> interceptInclusions = new ArrayList<>();

  /**
   * List of components not to intercept. Configured using
   * {@link TraceLevelConfiguration#interceptionDisabledComponents} on
   * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
   */
  private List<String> interceptDisabledByConfigComponents = new ArrayList<>();

  /**
   * List of components to intercept. Configured using
   * {@link TraceLevelConfiguration#interceptionDisabledComponents} on
   * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
   */
  private List<String> interceptEnabledByConfigComponents = new ArrayList<>();

  public InterceptorProcessorConfig setTurnOffTracing(boolean turnOffTracing) {
    this.turnOffTracing = turnOffTracing;
    return this;
  }

  public InterceptorProcessorConfig() {
    setupInterceptComponents();
  }

  public void updateTraceConfiguration(TraceLevelConfiguration traceLevelConfiguration) {
    interceptDisabledByConfigComponents = traceLevelConfiguration.getInterceptionDisabledComponents().stream()
        .map(MuleComponent::toString).collect(Collectors.toList());
    interceptEnabledByConfigComponents = traceLevelConfiguration.getInterceptionEnabledComponents().stream()
        .map(MuleComponent::toString).collect(Collectors.toList());
  }

  /**
   * Set the default list of components to intercept. This includes some core set
   * of processors.
   * Some known connector-operations that can propagate the OpenTelemetry context
   * to external services,
   * are also included to ensure the context (span id) references the current
   * processor instead of flow's span id.
   *
   * If users need to include/exclude any components they can do so by using
   * {@link TraceLevelConfiguration} on
   * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
   */
  private void setupInterceptComponents() {

    MuleCoreProcessorComponent.CORE_INTERCEPT_SCOPE_ROUTERS
        .forEach(c -> interceptInclusions.add("mule:" + c));

    try {
      InputStream interceptedComponentsFile = IOUtils.getResourceAsStream(
          "com/avioconsulting/mule/opentelemetry/internal/interceptor/intercept-components.txt",
          InterceptorProcessorConfig.class);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(interceptedComponentsFile))) {
        for (String line; (line = reader.readLine()) != null;) {
          if (line.startsWith("*")) {
            continue;
          }
          String[] split = line.split(":");
          LOGGER.trace("Attempting to add component to intercept: {}", line);
          if (split.length == 2) {
            interceptInclusions.add(line);
          } else {
            LOGGER.warn("Unable to parse intercept components entry: {}, skipping this line", line);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load interceptor components", e);
    }
  }

  public boolean interceptEnabled(ComponentLocation location) {
    if (!INTERCEPTOR_ENABLED_BY_SYS_PROPERTY) {
      LOGGER.trace("Interceptors are disabled by system property");
      return false;
    }
    if (turnOffTracing) {
      LOGGER.trace("Tracing has been turned off by global configuration");
      return false;
    }
    return ComponentsUtil.isFirstProcessor(location) || (NOT_FIRST_PROCESSOR_ONLY_MODE
        && interceptEnabled(location.getComponentIdentifier().getIdentifier()));
  }

  private boolean interceptEnabled(ComponentIdentifier componentIdentifier) {
    String identifier = componentIdentifier.getNamespace() + ":" + componentIdentifier.getName();
    String wildcardIdentifier = componentIdentifier.getNamespace() + ":*";
    if ((interceptDisabledByConfigComponents.contains(wildcardIdentifier)
        && !interceptEnabledByConfigComponents.contains(identifier))
        || interceptDisabledByConfigComponents.contains(identifier)) {
      LOGGER.trace("Component {} is disabled by global configuration", identifier);
      return false;
    } else if (interceptInclusions.contains(identifier)) {
      LOGGER.trace("Component {} is enabled by default configuration", identifier);
      return true;
    } else if (interceptEnabledByConfigComponents.contains(wildcardIdentifier)
        || interceptEnabledByConfigComponents.contains(identifier)) {
      LOGGER.trace("Component {} is enabled by global configuration", identifier);
      return true;
    } else {
      LOGGER.trace("Component {} is not configured for interception, skipping interception", identifier);
      return false;
    }
  }

}
