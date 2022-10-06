package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Optional;

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
public class FirstProcessorInterceptorFactory implements ProcessorInterceptorFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(FirstProcessorInterceptorFactory.class);
  public static final String MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME = "mule.otel.interceptor.processor.enable";
  private final boolean interceptorEnabled = Boolean
      .parseBoolean(System.getProperty(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, "true"));

  /**
   * {@link MuleNotificationProcessor} instance for getting opentelemetry
   * connection supplier by processor.
   */
  private final MuleNotificationProcessor muleNotificationProcessor;

  @Inject
  public FirstProcessorInterceptorFactory(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

  @Override
  public ProcessorInterceptor get() {
    return new ProcessorTracingInterceptor(muleNotificationProcessor);
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
      Optional<TypedComponentIdentifier> componentType = location.getParts().get(0).getPartIdentifier()
          .filter(c -> TypedComponentIdentifier.ComponentType.FLOW.equals(c.getType()));

      intercept = componentType.isPresent()
          && interceptPath.equalsIgnoreCase(location.getLocation());
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Will Intercept '{}'?: {}", location, intercept);
    }
    return intercept;
  }
}
