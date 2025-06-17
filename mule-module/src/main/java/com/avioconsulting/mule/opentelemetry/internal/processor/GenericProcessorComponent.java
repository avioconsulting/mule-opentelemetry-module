package com.avioconsulting.mule.opentelemetry.internal.processor;

import org.mule.runtime.api.component.ComponentIdentifier;

import java.util.Collections;
import java.util.List;

public class GenericProcessorComponent extends AbstractProcessorComponent {

  /**
   * This processor supports all components.
   * 
   * @param componentIdentifier
   *            {@link ComponentIdentifier}
   * @return <code>true</code> always.
   */
  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return true;
  }

  /**
   * This supports all namespaces.
   * 
   * @return {@link String}
   */
  @Override
  protected String getNamespace() {
    return NAMESPACE_MULE;
  }

  /**
   * This supports all operations.
   * 
   * @return {@link List}
   */
  @Override
  protected List<String> getOperations() {
    return Collections.emptyList();
  }

  /**
   * This supports all sources.
   * 
   * @return {@link List}
   */
  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

}
