package com.avioconsulting.mule.opentelemetry.internal.processor;

import java.util.Collections;
import java.util.List;

public class APIKitProcessorComponent extends AbstractProcessorComponent {
  @Override
  protected String getNamespace() {
    return "apikit";
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("router");
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

}
