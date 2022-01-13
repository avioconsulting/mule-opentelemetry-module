package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.springframework.stereotype.Component;

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

  @Override
  public ProcessorInterceptor get() {
    return new ProcessorTracingInterceptor();
  }

  /**
   * This intercepts the first processor of root container which can be a flow or
   * sub-flow.
   *
   * @param location
   * @{@link ComponentLocation}
   * @return
   */
  @Override
  public boolean intercept(ComponentLocation location) {
    return interceptorEnabled && location.getLocation().endsWith("/0");
  }
}
