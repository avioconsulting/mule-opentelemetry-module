package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import java.util.Map;
import java.util.Optional;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.mule.runtime.api.interception.SourceInterceptor;

/**
 * Doesn't let you modify the event before sending to flow but can let you
 * modify event before flow
 * response is sent. Could help in return the trace context
 */
public class TestSourceInterceptor implements SourceInterceptor {
  @Override
  public void beforeCallback(
      ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters,
      InterceptionEvent event) {
    System.out.println("######## Before callback");
  }

  @Override
  public void afterCallback(
      ComponentLocation location, InterceptionEvent event, Optional<Throwable> thrown) {
    System.out.println("######## After callback");
  }
}
