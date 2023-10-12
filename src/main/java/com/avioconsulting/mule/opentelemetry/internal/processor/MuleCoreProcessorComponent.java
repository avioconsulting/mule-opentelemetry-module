package com.avioconsulting.mule.opentelemetry.internal.processor;

import java.util.Collections;
import java.util.List;

/**
 * This processor handles any specific operations or sources from Mule Core
 * namespace that are needed for overall tracing.
 * Spans for these processors will be generated irrespective of
 * spanAllProcessors flag on global configuration.
 */
public class MuleCoreProcessorComponent extends AbstractProcessorComponent {

  @Override
  protected String getNamespace() {
    return NAMESPACE_MULE;
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("flow-ref");
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }
}
