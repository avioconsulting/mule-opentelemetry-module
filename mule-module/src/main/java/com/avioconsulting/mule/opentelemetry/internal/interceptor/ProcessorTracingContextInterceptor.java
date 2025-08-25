package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionAction;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ProcessorTracingContextInterceptor implements ProcessorInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTracingContextInterceptor.class);

  /**
   *
   * When processing around() methods, Mule Runtime will re-resolve any expression
   * parameters
   * on the component configurations.
   *
   * Consider the following flow implementation. When tracing this flow-
   *
   * - `logger` being the first processor (aka processor-0) gets intercepted by
   * {@link ProcessorTracingInterceptor#before(ComponentLocation, Map, InterceptionEvent)}
   * and adds OTEL_TRACE_CONTEXT into the flow variables
   *
   * - `http:request` is configured for interception, also gets intercepted by
   * {@link ProcessorTracingInterceptor#before(ComponentLocation, Map, InterceptionEvent)}.
   * For this processor, before() method will create a new span representing the
   * http:request processor and adds this new span as OTEL_TRACE_CONTEXT into the
   * flow variables. The outbound http request headers are expected to propagate
   * the context of this http request span.
   *
   * <pre>
   * {@code
   *     <flow name="test-flow>
   *         <logger level="INFO" message="#['Invoking HTTP']"/>
   *         <http:request method="GET" doc:name="Get Inventory"
   *          config-ref="http-request-config"
   *          path="/inventory">
   * 		 <http:headers><![CDATA[#[output application/java
   * ---
   * {
   * 	"traceparent": vars.OTEL_TRACE_CONTEXT.traceparent as String default ''
   * }]]]></http:headers>
   * 	    </http:request>
  *      </flow>
   * }
   * </pre>
   *
   * However, Mule runtime does not re-resolve the expression parameters with any
   * event modifications in before() methods.
   * This results in http:request's headers picking up the trace context of the
   * flow injected by processor-0, instead of http:request processor.
   *
   * When applying the around() method, runtime re-resolves all expression
   * parameters and thus causing the headers to pick expected context that was
   * injected by before() method intercepting the http:request itself.
   *
   * <pre>
   * <b>The implementation here is intentionally empty but exists to force runtime to refresh parameters values.</b>
   * </pre>
   *
   */
  @Override
  public CompletableFuture<InterceptionEvent> around(ComponentLocation location,
      Map<String, ProcessorParameterValue> parameters, InterceptionEvent event, InterceptionAction action) {
    // Intentionally empty, the method exists to force runtime to refresh previously
    // resolved parameters
    // with any modifications done by ProcessorTracingInterceptor#before method
    LOGGER.trace("Intercepted by around method '{}' at '{}'",
        location.getComponentIdentifier().getIdentifier().toString(), location.getLocation());
    return action.proceed();
  }

}
