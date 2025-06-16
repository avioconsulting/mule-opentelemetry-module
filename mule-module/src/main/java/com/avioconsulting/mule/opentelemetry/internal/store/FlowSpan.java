package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import com.avioconsulting.mule.opentelemetry.internal.util.PropertiesUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.avioconsulting.mule.opentelemetry.internal.processor.util.HttpSpanUtil.apiKitRoutePath;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

public class FlowSpan extends ContainerSpan {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowSpan.class);

  private String apikitConfigName;

  public FlowSpan(String containerName, Span span, TraceComponent traceComponent) {
    super(containerName, span, traceComponent);
  }

  public String getFlowName() {
    return getContainerName();
  }

  public String getApikitConfigName() {
    return apikitConfigName;
  }

  /**
   * Add a span created from given {@code SpanBuilder} for the processor
   * identified at the given location {@code String}.
   * When containerName {@code String} is provided, an existing span of that
   * container (eg. Flow) is set as the parent span of this processor span.
   *
   * @param containerName
   *            {@link String}
   * @param traceComponent
   *            {@link TraceComponent}
   * @param spanBuilder
   *            {@link SpanBuilder}
   * @return Span
   */
  public SpanMeta addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    SpanMeta spanMeta = super.addProcessorSpan(containerName, traceComponent, spanBuilder);
    extractAPIKitConfigName(traceComponent);
    resetSpanNameIfNeeded(traceComponent);
    return spanMeta;
  }

  private void resetSpanNameIfNeeded(TraceComponent traceComponent) {
    if (!PropertiesUtil.isUseAPIKitSpanNames())
      return;
    if (apikitConfigName != null && ComponentsUtil.isFlowTrace(traceComponent)
        && traceComponent.getName().endsWith(":" + apikitConfigName)) {
      if (getRootSpanName().endsWith("/*")) { // Wildcard listener for HTTP APIKit Router
        String apiKitRoutePath = apiKitRoutePath(traceComponent.getTags());
        String spanName = getRootSpanName().replace("/*", apiKitRoutePath);
        setRootSpanName(spanName);
        getSpan().updateName(spanName);
        // HTTP Span Name of the format '{METHOD} {ROUTE}'
        String httpRoute = spanName.substring(spanName.lastIndexOf(" ") + 1);
        getTags().put(HTTP_ROUTE.getKey(), httpRoute);
        getSpan().setAttribute(HTTP_ROUTE, httpRoute);
      }
    }
  }

  private void extractAPIKitConfigName(TraceComponent traceComponent) {
    if (apikitConfigName == null
        && "apikit"
            .equals(traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey()))
        && "router".equals(traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey()))) {
      apikitConfigName = traceComponent.getTags().get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey());
    }
  }

  public boolean childFlowsEnded() {
    return childContainersEnded();
  }
}
