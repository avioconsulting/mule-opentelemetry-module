package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY;
import static com.avioconsulting.mule.opentelemetry.internal.interceptor.InterceptorProcessorConfig.MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.isFirstProcessor;

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
public class ProcessorTracingInterceptorFactory implements ProcessorInterceptorFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingInterceptorFactory.class);
  private final boolean interceptorEnabled = PropertiesUtil
      .getBoolean(MULE_OTEL_INTERCEPTOR_PROCESSOR_ENABLE_PROPERTY_NAME, true);

  // Negating the property value since usage is negated
  private final boolean NOT_FIRST_PROCESSOR_ONLY_MODE = !PropertiesUtil
      .getBoolean(MULE_OTEL_INTERCEPTOR_FIRST_PROCESSOR_ONLY, false);

  /**
   * {@link MuleNotificationProcessor} instance for getting opentelemetry
   * connection supplier by processor.
   */
  private final ProcessorTracingInterceptor processorTracingInterceptor;

  @Inject
  public ProcessorTracingInterceptorFactory(MuleNotificationProcessor muleNotificationProcessor) {
    processorTracingInterceptor = new ProcessorTracingInterceptor(muleNotificationProcessor);
  }

  @Override
  public ProcessorInterceptor get() {
    return processorTracingInterceptor;
  }

  /**
   * This intercepts the first processor of root container which can be a flow or
   * sub-flow.
   *
   * When `mule.otel.interceptor.first.processor.only` is NOT set to 'true', every
   * processor will be intercepted.
   * See
   * {@link InterceptorProcessorConfig#shouldIntercept(ComponentLocation, org.mule.runtime.api.event.Event)}
   * for how intercepting decisions are made at runtime for each location.
   *
   * This will not intercept ANY processor if
   * "mule.otel.interceptor.processor.enable" is set to
   * `false` Or {@link MuleNotificationProcessor} does not have a valid connection
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
    if (interceptorEnabled) {
      intercept = (isFirstProcessor(location)
          || NOT_FIRST_PROCESSOR_ONLY_MODE);
    }
    if (LOGGER.isTraceEnabled() && intercept) {
      LOGGER.trace("Will Intercept '{}::{}'", location.getRootContainerName(), location.getLocation());
    }
    return intercept;
  }

}
