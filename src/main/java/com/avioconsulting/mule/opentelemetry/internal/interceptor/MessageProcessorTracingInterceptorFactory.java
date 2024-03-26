package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.api.config.MuleComponent;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.*;

/**
 * ProcessorInterceptorFactory can intercept processors. This is injected
 * registry for auto-configuration.
 *
 * Disable interceptor processing by setting
 * "mule.otel.interceptor.processor.enable" to `true`.
 *
 * See registry-bootstrap.properties.
 */
@Component
public class MessageProcessorTracingInterceptorFactory implements ProcessorInterceptorFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessorTracingInterceptorFactory.class);
  public static final String MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME = "mule.otel.interceptor.processor.enable";
  private final boolean interceptorEnabled = Boolean
      .parseBoolean(System.getProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "true"));
  /**
   * {@link MuleNotificationProcessor} instance for getting opentelemetry
   * connection supplier by processor.
   */
  private final ProcessorTracingInterceptor processorTracingInterceptor;
  private final MuleNotificationProcessor muleNotificationProcessor;;

  private final List<MuleComponent> interceptExclusions = new ArrayList<>();

  private final List<MuleComponent> interceptInclusions = new ArrayList<>();

  @Inject
  public MessageProcessorTracingInterceptorFactory(MuleNotificationProcessor muleNotificationProcessor,
      ConfigurationComponentLocator configurationComponentLocator) {
    processorTracingInterceptor = new ProcessorTracingInterceptor(muleNotificationProcessor,
        configurationComponentLocator);
    this.muleNotificationProcessor = muleNotificationProcessor;
    setupInterceptableComponents(muleNotificationProcessor);
  }

  /**
   * Exclude following {@link MuleComponent}s -
   * 
   * <pre>
   * - {@code ee:* } - All components such as cache, transform component, dynamic evaluate from ee namespace
   * - {@code mule:*} - All components from mule namespace except from {@link #interceptInclusions}
   *
   * </pre>
   */
  private void setupInterceptableComponents(MuleNotificationProcessor muleNotificationProcessor) {

    String excludedNamespaces = "ee,mule,validations,aggregators,json,oauth,scripting,tracing,oauth2-provider,xml,wss,spring,java,avio-logger";

    Stream.of(excludedNamespaces.split(","))
        .forEach(ns -> interceptExclusions.add(new MuleComponent(ns, "*")));

    interceptInclusions.add(new MuleComponent("mule", "flow-ref"));

    if (muleNotificationProcessor.getTraceLevelConfiguration() != null) {
      if (muleNotificationProcessor.getTraceLevelConfiguration().getInterceptionDisabledComponents() != null)
        interceptExclusions
            .addAll(muleNotificationProcessor.getTraceLevelConfiguration()
                .getInterceptionDisabledComponents());
      if (muleNotificationProcessor.getTraceLevelConfiguration().getInterceptionEnabledComponents() != null)
        interceptInclusions
            .addAll(muleNotificationProcessor.getTraceLevelConfiguration()
                .getInterceptionEnabledComponents());
    }
  }

  @Override
  public ProcessorInterceptor get() {
    return processorTracingInterceptor;
  }

  public List<MuleComponent> getInterceptExclusions() {
    return interceptExclusions;
  }

  public List<MuleComponent> getInterceptInclusions() {
    return interceptInclusions;
  }

  /**
   * This intercepts the first processor of root container which can be a flow or
   * sub-flow.
   *
   * This will not intercept if "mule.otel.interceptor.processor.enable" is set to
   * `true` Or {@link MuleNotificationProcessor} does not have a valid connection
   * due to disabled tracing. See
   * {@link OpenTelemetryExtensionConfiguration#start()}.
   *
   * @param location
   *            {@link ComponentLocation}
   * @return true if intercept
   */
  @Override
  public boolean intercept(ComponentLocation location) {
    boolean intercept = false;
    if (interceptorEnabled &&
        muleNotificationProcessor.hasConnection()) {
      String interceptPath = String.format("%s/processors/0", location.getRootContainerName());
      // Intercept the first processor of the flow OR
      // included processor/namespaces OR
      // any processor/namespaces that are not excluded
      ComponentIdentifier identifier = location.getComponentIdentifier().getIdentifier();
      boolean firstProcessor = isFlowTypeContainer(location)
          && interceptPath.equalsIgnoreCase(location.getLocation());
      boolean interceptConfigured = interceptInclusions.stream()
          .anyMatch(mc -> mc.getNamespace().equalsIgnoreCase(identifier.getNamespace())
              & (mc.getName().equalsIgnoreCase(identifier.getName())
                  || "*".equalsIgnoreCase(mc.getName())))
          || interceptExclusions.stream()
              .noneMatch(mc -> mc.getNamespace().equalsIgnoreCase(identifier.getNamespace())
                  & (mc.getName().equalsIgnoreCase(identifier.getName())
                      || "*".equalsIgnoreCase(mc.getName())));
      intercept = firstProcessor
          || interceptConfigured;

      if (intercept) {
        // This factory executes during application initialization.
        // Let's reuse the intercept decisions for excluding span creation in
        // notification processor.
        // This will let us avoid the lookup for each component in notification
        // processor.
        muleNotificationProcessor.addInterceptSpannedComponents(location.getLocation());
        if (interceptConfigured) {
          // Exclude any first processors that are generic processors such as loggers
          muleNotificationProcessor.addMeteredComponentLocation(location.getLocation());
        }
      }
    }
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Will Intercept '{}'?: {}", location, intercept);
    }
    return intercept;
  }

  private boolean isFlowTypeContainer(ComponentLocation componentLocation) {
    return componentLocation.getParts().get(0).getPartIdentifier()
        .filter(c -> FLOW.equals(c.getType())
            || (SCOPE.equals(c.getType())))
        .isPresent();
  }
}
