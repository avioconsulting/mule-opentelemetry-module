package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

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
    return muleNotificationProcessor.hasConnection() &&
        interceptorEnabled && location.getLocation().endsWith("/0");
  }
}
