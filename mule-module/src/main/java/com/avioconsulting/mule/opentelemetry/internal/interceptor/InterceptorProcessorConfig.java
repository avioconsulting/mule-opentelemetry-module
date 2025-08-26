package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleCoreProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.core.api.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.*;

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
   * Configuration key representing enabled processors interception.
   * These should be the components that require to inject its own trace context
   * into flow variables.
   *
   * Value should be a comma-separated string of namespace:operation format,
   * For example, http:request,jms:publish...
   *
   * Module includes a predefined list of processors configured for interception.
   * Use this property only if your processor isn't included.
   */
  public static final String MULE_OTEL_INTERCEPTOR_ENABLED_PROCESSORS = "mule.otel.interceptor.enabled.processors";
  private final String interceptorEnabledProcessors = PropertiesUtil
      .getProperty(MULE_OTEL_INTERCEPTOR_ENABLED_PROCESSORS);

  /**
   * Configuration key representing disable processors interception.
   * This is useful in case any default intercepted component is causing issued.
   *
   * Value should be a comma-separated string of namespace:operation format,
   * For example, http:request,jms:publish...
   */
  public static final String MULE_OTEL_INTERCEPTOR_DISABLED_PROCESSORS = "mule.otel.interceptor.disabled.processors";
  private final String interceptorDisabledProcessors = PropertiesUtil
      .getProperty(MULE_OTEL_INTERCEPTOR_DISABLED_PROCESSORS);
  /**
   * Configuration key representing enabled processors for OpenTelemetry context
   * propagation.
   * This property allows the module to configure around() interceptor to these
   * processors
   * and thus have OpenTelemetry context available during processor execution.
   *
   * Value should be a comma-separated string of namespace:operation format,
   * For example, http:request,jms:publish...
   *
   * Module includes a predefined list of processors configured for context
   * propagation.
   * Use this property only if your processor isn't included.
   */
  public static final String MULE_OTEL_INTERCEPTOR_CONTEXT_ENABLED_PROCESSORS = "mule.otel.interceptor.context.enabled.processors";
  private final String contextEnabledProcessors = PropertiesUtil
      .getProperty(MULE_OTEL_INTERCEPTOR_CONTEXT_ENABLED_PROCESSORS);

  /**
   * If the processor is configured to use context propagation interceptor but
   * causing any issues,
   * this property can be used to exclude processors from interception.
   *
   * Value should be a comma-separated string of namespace:operation format,
   * For example, http:request,jms:publish...
   *
   */
  public static final String MULE_OTEL_INTERCEPTOR_CONTEXT_DISABLED_PROCESSORS = "mule.otel.interceptor.context.disabled.processors";
  private final String contextDisabledProcessors = PropertiesUtil
      .getProperty(MULE_OTEL_INTERCEPTOR_CONTEXT_DISABLED_PROCESSORS);

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
  private final Set<String> interceptInclusions = new HashSet<>();
  private final Set<String> propagationRequiredComponents = new HashSet<>();

  private ComponentRegistryService componentRegistryService;
  /**
   * List of components not to intercept. Configured using
   * {@link TraceLevelConfiguration#getInterceptionDisabledComponents()} on
   * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
   */
  private Set<String> interceptDisabledByConfigComponents = new HashSet<>();

  /**
   * List of components to intercept. Configured using
   * {@link TraceLevelConfiguration#getInterceptionEnabledComponents()} on
   * {@link com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration}.
   */
  private Set<String> interceptEnabledByConfigComponents = new HashSet<>();

  public InterceptorProcessorConfig setTurnOffTracing(boolean turnOffTracing) {
    this.turnOffTracing = turnOffTracing;
    return this;
  }

  public InterceptorProcessorConfig setComponentRegistryService(ComponentRegistryService componentRegistryService) {
    this.componentRegistryService = componentRegistryService;
    return this;
  }

  public InterceptorProcessorConfig() {
    setupInterceptComponents();
  }

  /**
   * {@link Deprecated} since 2.10.1 Use system properties
   * `mule.otel.interceptor.enabled.processors` or
   * `mule.otel.interceptor.disabled.processors`.
   *
   * @param traceLevelConfiguration
   */
  @Deprecated()
  public void updateTraceConfiguration(TraceLevelConfiguration traceLevelConfiguration) {
    interceptDisabledByConfigComponents = traceLevelConfiguration.getInterceptionDisabledComponents().stream()
        .map(MuleComponent::toString).collect(Collectors.toSet());
    interceptEnabledByConfigComponents = traceLevelConfiguration.getInterceptionEnabledComponents().stream()
        .map(MuleComponent::toString).collect(Collectors.toSet());
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

    if (interceptorEnabledProcessors != null && !interceptorEnabledProcessors.trim().isEmpty()) {
      Set<String> processors = splitByComma(interceptorEnabledProcessors);
      interceptEnabledByConfigComponents.addAll(processors);
    }
    if (interceptorDisabledProcessors != null && !interceptorDisabledProcessors.trim().isEmpty()) {
      Set<String> processors = splitByComma(interceptorDisabledProcessors);
      interceptDisabledByConfigComponents.addAll(processors);
    }
    setupContextPropagationRequiredComponents();
  }

  /**
   * Mule operations that require context propagation, must be intercepted with
   * {@link ProcessorTracingContextInterceptor#around(ComponentLocation, Map, InterceptionEvent, InterceptionAction)}
   * method for context variable propagation
   */
  private void setupContextPropagationRequiredComponents() {
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
            propagationRequiredComponents.add(line);
          } else {
            LOGGER.warn("Unable to parse intercept components entry: {}, skipping this line", line);
          }
        }
      }

      if (contextEnabledProcessors != null && !contextEnabledProcessors.trim().isEmpty()) {
        LOGGER.info("Intercepting additional processor configured with sys/env property {} : {}",
            MULE_OTEL_INTERCEPTOR_CONTEXT_ENABLED_PROCESSORS, contextEnabledProcessors);
        Set<String> processors = splitByComma(contextEnabledProcessors);
        interceptInclusions.addAll(processors);
        propagationRequiredComponents.addAll(processors);
      }

      if (contextDisabledProcessors != null && !contextDisabledProcessors.trim().isEmpty()) {
        LOGGER.info("Removing processor configured with sys/env property {} from interception: {}",
            MULE_OTEL_INTERCEPTOR_CONTEXT_ENABLED_PROCESSORS, contextEnabledProcessors);
        Set<String> processors = splitByComma(contextDisabledProcessors);
        propagationRequiredComponents.removeAll(processors);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load interceptor components", e);
    }
    LOGGER.info("Final list of Context Intercepted components: {}", propagationRequiredComponents);
  }

  private Set<String> splitByComma(String input) {
    return Arrays.stream(input.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  public boolean shouldIntercept(ComponentLocation location, Event event) {
    if (!interceptorFeatureEnabled())
      return false;

    if (event != null && shouldSkipThisBatchProcessing(event))
      return false;
    return ComponentsUtil.isFirstProcessor(location)
        || (event != null && isBatchStepFirstProcessor(location, event, componentRegistryService))
        || (NOT_FIRST_PROCESSOR_ONLY_MODE
            && (shouldIntercept(location.getComponentIdentifier().getIdentifier())));
  }

  private boolean interceptorFeatureEnabled() {
    if (!INTERCEPTOR_ENABLED_BY_SYS_PROPERTY) {
      LOGGER.trace("Interceptors are disabled by system property");
      return false;
    }
    if (turnOffTracing) {
      LOGGER.trace("Tracing has been turned off by global configuration");
      return false;
    }
    return true;
  }

  public boolean interceptAround(ComponentLocation location) {
    if (!interceptorFeatureEnabled())
      return false;
    String identifier = location.getComponentIdentifier().getIdentifier().getNamespace() + ":"
        + location.getComponentIdentifier().getIdentifier().getName();
    boolean intercept = propagationRequiredComponents.contains(identifier);
    if (intercept) {
      LOGGER.trace("Component {} is configured for context propagation, will intercept", identifier);
    }
    return intercept;
  }

  private boolean shouldIntercept(ComponentIdentifier componentIdentifier) {
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
