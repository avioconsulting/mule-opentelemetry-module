package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorInterceptorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessorTracingContextInterceptorFactory implements ProcessorInterceptorFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingContextInterceptorFactory.class);

  private final ProcessorTracingContextInterceptor processorTracingContextInterceptor = new ProcessorTracingContextInterceptor();
  private final InterceptorProcessorConfig interceptorProcessorConfig = new InterceptorProcessorConfig();

  @Override
  public ProcessorInterceptor get() {
    return processorTracingContextInterceptor;
  }

  @Override
  public boolean intercept(ComponentLocation location) {
    boolean intercept = interceptorProcessorConfig.interceptAround(location);
    if (intercept) {
      LOGGER.trace("ProcessorTracingContextInterceptor will Intercept '{}::{}'", location.getRootContainerName(),
          location.getLocation());
    }
    return intercept;
  }
}
