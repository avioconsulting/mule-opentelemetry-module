package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

  @Override
  protected String getDefaultSpanName(Map<String, String> tags) {
    return super.getDefaultSpanName(tags).concat(" ")
        .concat(tags.get(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey()));
  }
}
