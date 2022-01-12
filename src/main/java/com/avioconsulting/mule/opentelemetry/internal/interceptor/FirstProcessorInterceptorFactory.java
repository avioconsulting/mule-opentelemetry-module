package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.springframework.stereotype.Component;

/**
 * ProcessorInterceptorFactory can intercept processors. This is injected
 * registry for
 * auto-configuration. See registry-bootstrap.properties.
 */
@Component
public class FirstProcessorInterceptorFactory implements ProcessorInterceptorFactory {
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
    return location.getLocation().endsWith("/0");
  }
}
